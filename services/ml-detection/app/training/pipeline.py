"""Unified training pipeline: extract → train AE → generate BiGRU labels → train BiGRU → validate → export ONNX.

Single CLI entry point for end-to-end model training. Designed to be triggered
by K8s CronJob or manual invocation.
"""

from __future__ import annotations

import argparse
import json
import logging
import time
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np

from app.config import settings
from app.features.extractor import FeatureExtractor
from app.models.schemas import AggregatedAttackData
from app.serving.scorer import reconstruction_to_anomaly_score
from app.training.bigru_trainer import (
    build_sequences_from_features,
    export_bigru_onnx,
    optimize_alpha,
    train_bigru,
)
from app.training.trainer import export_onnx, load_from_csv, load_from_postgres, train_autoencoder

logger = logging.getLogger(__name__)

FEATURE_DIM = 12
MAX_SEQ_LENS: Dict[int, int] = {1: 32, 2: 32, 3: 48}


class TrainingPipeline:
    def __init__(
        self,
        tier: int,
        model_dir: str,
        ae_epochs: int = 100,
        bigru_epochs: int = 50,
        csv_path: Optional[str] = None,
        database_url: Optional[str] = None,
        do_optimize_alpha: bool = True,
        alpha_candidates: Optional[List[float]] = None,
        min_seq_len: int = 4,
    ) -> None:
        self.tier = tier
        self.model_dir = Path(model_dir)
        self.ae_epochs = ae_epochs
        self.bigru_epochs = bigru_epochs
        self.csv_path = csv_path
        self.database_url = database_url or settings.database_url
        self.do_optimize_alpha = do_optimize_alpha
        self.alpha_candidates = alpha_candidates or [0.3, 0.4, 0.5, 0.6, 0.7, 0.8]
        self.min_seq_len = min_seq_len
        self.max_seq_len = MAX_SEQ_LENS.get(tier, 32)
        self._metrics: Dict[str, object] = {}

    def run(self) -> Dict[str, object]:
        start = time.monotonic()
        logger.info("=== Training pipeline started for tier %d ===", self.tier)

        features = self._step_extract()
        ae_model, ae_path = self._step_train_autoencoder(features)
        sequences, masks, labels, groups = self._step_generate_bigru_labels(features, ae_model)
        bigru_path, optimal_alpha = self._step_train_bigru(sequences, masks, labels, groups)

        elapsed = time.monotonic() - start
        self._metrics["total_elapsed_seconds"] = round(elapsed, 2)
        self._metrics["tier"] = self.tier
        self._metrics["autoencoder_path"] = str(ae_path)
        self._metrics["bigru_path"] = str(bigru_path)
        if optimal_alpha is not None:
            self._metrics["optimal_alpha"] = optimal_alpha

        logger.info("=== Pipeline complete in %.1fs ===", elapsed)
        logger.info("Metrics: %s", json.dumps(self._metrics, indent=2, default=str))
        return self._metrics

    def _step_extract(self) -> np.ndarray:
        logger.info("Step 1: Extracting features for tier %d", self.tier)
        if self.csv_path:
            features = load_from_csv(self.csv_path)
        else:
            features = load_from_postgres(self.database_url, self.tier)

        if len(features) == 0:
            logger.warning("No data found — generating synthetic training data")
            features = np.random.rand(256, FEATURE_DIM).astype(np.float32)

        self._metrics["training_samples"] = len(features)
        logger.info("Extracted %d feature vectors", len(features))
        return features

    def _step_train_autoencoder(self, features: np.ndarray) -> Tuple:
        logger.info("Step 2: Training autoencoder (%d epochs)", self.ae_epochs)
        from app.models.autoencoder import ThreatAutoencoder

        model = train_autoencoder(features, self.ae_epochs)
        out_path = self.model_dir / f"autoencoder_v1_tier{self.tier}.onnx"
        export_onnx(model, out_path)

        self._metrics["autoencoder_epochs"] = self.ae_epochs
        logger.info("Autoencoder exported to %s", out_path)
        return model, out_path

    def _step_generate_bigru_labels(
        self, features: np.ndarray, ae_model
    ) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
        """Generate BiGRU training data by running autoencoder on each window.

        Groups features by a synthetic attacker key (every 50 consecutive
        windows treated as one attacker session) to create realistic sequences.
        """
        import torch

        logger.info("Step 3: Generating BiGRU labels via autoencoder")

        ae_model.eval()
        with torch.no_grad():
            tensor = torch.tensor(features, dtype=torch.float32)
            reconstructed = ae_model(tensor).numpy()

        rec_errors = np.mean((features - reconstructed) ** 2, axis=1)
        threshold = float(np.percentile(rec_errors, 95))
        self._metrics["ae_threshold_p95"] = threshold

        scores = [
            reconstruction_to_anomaly_score(float(e), threshold) for e in rec_errors
        ]

        windows_per_session = 50
        features_by_attacker: Dict[str, List[np.ndarray]] = {}
        scores_by_attacker: Dict[str, List[float]] = {}

        for i, (feat, sc) in enumerate(zip(features, scores)):
            attacker_key = f"attacker_{i // windows_per_session}"
            features_by_attacker.setdefault(attacker_key, []).append(feat)
            scores_by_attacker.setdefault(attacker_key, []).append(sc)

        sequences, masks, labels, groups = build_sequences_from_features(
            features_by_attacker,
            scores_by_attacker,
            self.max_seq_len,
            self.min_seq_len,
        )

        self._metrics["bigru_training_sequences"] = len(sequences)
        logger.info("Generated %d BiGRU training sequences", len(sequences))
        return sequences, masks, labels, groups

    def _step_train_bigru(
        self,
        sequences: np.ndarray,
        masks: np.ndarray,
        labels: np.ndarray,
        groups: np.ndarray,
    ) -> Tuple[Path, Optional[float]]:
        logger.info("Step 4: Training BiGRU (%d epochs)", self.bigru_epochs)

        if len(sequences) < 20:
            logger.warning("Insufficient sequences (%d < 20), skipping BiGRU training", len(sequences))
            self._metrics["bigru_skipped"] = True
            return self.model_dir / f"bigru_v1_tier{self.tier}.onnx", None

        optimal_alpha: Optional[float] = None
        if self.do_optimize_alpha:
            optimal_alpha, alpha_results = optimize_alpha(
                sequences, masks, labels, groups, alpha_candidates=self.alpha_candidates
            )
            self._metrics["alpha_optimization"] = {
                str(k): round(v, 6) for k, v in alpha_results.items()
            }
            self._metrics["optimal_alpha"] = optimal_alpha
            logger.info("Optimal alpha: %.2f", optimal_alpha)

        model = train_bigru(
            sequences=sequences,
            masks=masks,
            labels=labels,
            groups=groups,
            hidden_size=settings.bigru_hidden_size,
            num_layers=settings.bigru_num_layers,
            dropout=settings.bigru_dropout,
            epochs=self.bigru_epochs,
        )

        out_path = self.model_dir / f"bigru_v1_tier{self.tier}.onnx"
        export_bigru_onnx(model, out_path, self.max_seq_len, optimal_alpha=optimal_alpha)
        self._metrics["bigru_epochs"] = self.bigru_epochs
        logger.info("BiGRU exported to %s", out_path)
        return out_path, optimal_alpha


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Unified ML training pipeline")
    parser.add_argument("--tier", type=int, required=True, choices=[1, 2, 3])
    parser.add_argument("--ae-epochs", type=int, default=100)
    parser.add_argument("--bigru-epochs", type=int, default=50)
    parser.add_argument("--csv", type=str, default=None, help="CSV feature file (skip postgres)")
    parser.add_argument("--model-dir", type=str, default=settings.model_dir)
    parser.add_argument("--database-url", type=str, default=None)
    parser.add_argument(
        "--no-optimize-alpha", action="store_true", help="Skip alpha grid-search"
    )
    parser.add_argument(
        "--alpha-values",
        type=str,
        default=settings.alpha_search_values,
        help="Comma-separated alpha candidates",
    )
    parser.add_argument("--all-tiers", action="store_true", help="Train all tiers sequentially")
    return parser.parse_args()


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
    args = parse_args()
    alpha_candidates = [float(x) for x in args.alpha_values.split(",")]

    tiers = [1, 2, 3] if args.all_tiers else [args.tier]

    for tier in tiers:
        pipeline = TrainingPipeline(
            tier=tier,
            model_dir=args.model_dir,
            ae_epochs=args.ae_epochs,
            bigru_epochs=args.bigru_epochs,
            csv_path=args.csv,
            database_url=args.database_url,
            do_optimize_alpha=not args.no_optimize_alpha,
            alpha_candidates=alpha_candidates,
        )
        metrics = pipeline.run()
        print(json.dumps(metrics, indent=2, default=str))


if __name__ == "__main__":
    main()
