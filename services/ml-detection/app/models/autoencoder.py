import torch
from torch import nn


class ThreatAutoencoder(nn.Module):
    def __init__(self, input_dim: int = 12, latent_dim: int = 6) -> None:
        super().__init__()
        self.encoder = nn.Sequential(
            nn.Linear(input_dim, 10),
            nn.BatchNorm1d(10),
            nn.ReLU(),
            nn.Dropout(0.1),
            nn.Linear(10, 8),
            nn.BatchNorm1d(8),
            nn.ReLU(),
            nn.Dropout(0.1),
            nn.Linear(8, latent_dim),
            nn.BatchNorm1d(latent_dim),
            nn.ReLU(),
        )
        self.decoder = nn.Sequential(
            nn.Linear(latent_dim, 8),
            nn.BatchNorm1d(8),
            nn.ReLU(),
            nn.Linear(8, 10),
            nn.BatchNorm1d(10),
            nn.ReLU(),
            nn.Linear(10, input_dim),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        latent = self.encoder(x)
        reconstruction = self.decoder(latent)
        return reconstruction
