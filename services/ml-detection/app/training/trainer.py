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


def load_from_postgres(database_url: str, tier: int) -> np.ndarray:
    conn = psycopg2.connect(database_url)
    try:
        with conn.cursor() as cursor:
            cursor.execute(
                """
                SELECT
                    LN(1 + GREATEST(attack_count, 0)) AS attack_count_log,
                    GREATEST(unique_ips, 0)::float AS unique_ips,
                    GREATEST(unique_ports, 0)::float AS unique_ports,
                    GREATEST(unique_devices, 0)::float / 10.0 AS unique_devices_norm,
                    COALESCE(mixed_port_weight, 1.0)::float AS mixed_port_weight,
                    LN(1 + GREATEST(net_weight, 0))::float AS net_weight_log,
                    COALESCE(intel_score, 0)::float / 100.0 AS intel_score_norm,
                    LN(1 + GREATEST(event_time_span, 0) / 1000.0)::float AS event_time_span_log,
                    COALESCE(burst_intensity, 0.0)::float AS burst_intensity,
                    COALESCE(time_distribution_weight, 1.0)::float AS time_dist_weight,
                    SIN(2 * PI() * EXTRACT(HOUR FROM window_start) / 24.0)::float AS hour_sin,
                    COS(2 * PI() * EXTRACT(HOUR FROM window_start) / 24.0)::float AS hour_cos
                FROM threat_alerts
                WHERE tier = %s
                """,
                (tier,),
            )
            rows = cursor.fetchall()
    finally:
        conn.close()

    if not rows:
        return np.zeros((0, 12), dtype=np.float32)
    return np.asarray(rows, dtype=np.float32)


def train_autoencoder(features: np.ndarray, epochs: int, batch_size: int = 64) -> ThreatAutoencoder:
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


def run_training(tier: int, epochs: int, csv_path: Optional[str], model_dir: str) -> Path:
    features = load_from_csv(csv_path) if csv_path else load_from_postgres(settings.database_url, tier)
    if len(features) == 0:
        features = np.random.rand(256, 12).astype(np.float32)

    model = train_autoencoder(features=features, epochs=epochs)
    out_path = Path(model_dir) / f"autoencoder_v1_tier{tier}.onnx"
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
    output = run_training(tier=args.tier, epochs=args.epochs, csv_path=args.csv, model_dir=args.model_dir)
    print(str(output))


if __name__ == "__main__":
    main()
