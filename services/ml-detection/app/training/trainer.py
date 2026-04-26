from __future__ import annotations

import argparse
from pathlib import Path
from typing import Optional

import numpy as np
import psycopg2
import torch
from torch import nn
from torch.utils.data import DataLoader, TensorDataset

from app.config import settings
from app.models.autoencoder import ThreatAutoencoder


def load_from_csv(path: str) -> np.ndarray:
    data = np.loadtxt(path, delimiter=",", dtype=np.float32)
    if data.ndim == 1:
        data = np.expand_dims(data, 0)
    return data[:, :12]


def load_from_postgres(
    database_url: str, tier: int, customer_id: Optional[str] = None
) -> np.ndarray:
    """Load 12-dim feature vectors from threat_assessments table.

    Feature mapping (aligned with actual DB schema):
      0: attack_count_log     — LN(1 + attack_count)
      1: unique_ips           — raw unique IP count
      2: unique_ports         — raw unique port count
      3: unique_devices_norm  — unique_devices / 10.0
      4: port_risk_score      — COALESCE(port_risk_score, 1.0)
      5: threat_score_log     — LN(1 + threat_score) as proxy for net weight
      6: ml_weight_norm       — COALESCE(ml_weight, 1.0) / 3.0
      7: time_weight          — COALESCE(time_weight, 1.0)
      8: ip_weight            — COALESCE(ip_weight, 1.0)
      9: port_weight          — COALESCE(port_weight, 1.0)
     10: hour_sin             — SIN(2π × hour / 24)
     11: hour_cos             — COS(2π × hour / 24)
    """
    import logging

    _logger = logging.getLogger(__name__)

    try:
        conn = psycopg2.connect(database_url)
    except Exception as e:
        _logger.warning("Cannot connect to postgres: %s — returning empty", e)
        return np.zeros((0, 12), dtype=np.float32)

    try:
        with conn.cursor() as cursor:
            where_clauses = ["detection_tier = %s"]
            params: list = [tier]
            if customer_id is not None:
                where_clauses.append("customer_id = %s")
                params.append(customer_id)

            cursor.execute(
                f"""
                SELECT
                    LN(1 + GREATEST(attack_count, 0))::float             AS attack_count_log,
                    GREATEST(unique_ips, 0)::float                       AS unique_ips,
                    GREATEST(unique_ports, 0)::float                     AS unique_ports,
                    GREATEST(unique_devices, 0)::float / 10.0            AS unique_devices_norm,
                    COALESCE(port_risk_score, 1.0)::float                AS port_risk_score,
                    LN(1 + GREATEST(threat_score, 0))::float             AS threat_score_log,
                    COALESCE(ml_weight, 1.0)::float / 3.0               AS ml_weight_norm,
                    COALESCE(time_weight, 1.0)::float                    AS time_weight,
                    COALESCE(ip_weight, 1.0)::float                      AS ip_weight,
                    COALESCE(port_weight, 1.0)::float                    AS port_weight,
                    SIN(2 * PI() * EXTRACT(HOUR FROM assessment_time) / 24.0)::float AS hour_sin,
                    COS(2 * PI() * EXTRACT(HOUR FROM assessment_time) / 24.0)::float AS hour_cos
                FROM threat_assessments
                WHERE {" AND ".join(where_clauses)}
                ORDER BY assessment_time DESC
                LIMIT 10000
                """,
                tuple(params),
            )
            rows = cursor.fetchall()
    except Exception as e:
        _logger.warning("Failed to query threat_assessments: %s — returning empty", e)
        return np.zeros((0, 12), dtype=np.float32)
    finally:
        conn.close()

    if not rows:
        return np.zeros((0, 12), dtype=np.float32)
    return np.asarray(rows, dtype=np.float32)


def train_autoencoder(
    features: np.ndarray, epochs: int, batch_size: int = 64
) -> ThreatAutoencoder:
    model = ThreatAutoencoder(input_dim=12, latent_dim=6)
    model.train()

    tensor = torch.tensor(features, dtype=torch.float32)
    loader = DataLoader(TensorDataset(tensor), batch_size=batch_size, shuffle=True)
    optimizer = torch.optim.Adam(model.parameters(), lr=1e-3)
    criterion = nn.MSELoss()

    for _ in range(epochs):
        for (batch,) in loader:
            optimizer.zero_grad()
            reconstructed = model(batch)
            loss = criterion(reconstructed, batch)
            loss.backward()
            optimizer.step()

    model.eval()
    return model


def export_onnx(model: ThreatAutoencoder, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    sample = torch.randn(1, 12, dtype=torch.float32)
    torch.onnx.export(
        model,
        sample,
        str(output_path),
        input_names=["input"],
        output_names=["reconstruction"],
        dynamic_axes={"input": {0: "batch_size"}, "reconstruction": {0: "batch_size"}},
        opset_version=17,
    )


def run_training(
    tier: int,
    epochs: int,
    csv_path: Optional[str],
    model_dir: str,
    customer_id: Optional[str] = None,
) -> Path:
    features = (
        load_from_csv(csv_path)
        if csv_path
        else load_from_postgres(settings.database_url, tier, customer_id=customer_id)
    )
    if len(features) == 0:
        features = np.random.rand(256, 12).astype(np.float32)

    model = train_autoencoder(features=features, epochs=epochs)
    subdir = customer_id if customer_id else "global"
    out_path = Path(model_dir) / subdir / f"autoencoder_v1_tier{tier}.onnx"
    export_onnx(model, out_path)
    return out_path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tier", type=int, required=True, choices=[1, 2, 3])
    parser.add_argument("--epochs", type=int, default=100)
    parser.add_argument("--csv", type=str, default=None)
    parser.add_argument("--model-dir", type=str, default=settings.model_dir)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    output = run_training(
        tier=args.tier, epochs=args.epochs, csv_path=args.csv, model_dir=args.model_dir
    )
    print(str(output))


if __name__ == "__main__":
    main()
