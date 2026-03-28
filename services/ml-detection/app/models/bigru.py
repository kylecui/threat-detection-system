from __future__ import annotations

import torch
from torch import nn


class AdditiveAttention(nn.Module):
    """Bahdanau-style additive attention over BiGRU hidden states."""

    def __init__(self, hidden_size: int) -> None:
        super().__init__()
        self.energy = nn.Linear(hidden_size, hidden_size)
        self.v = nn.Linear(hidden_size, 1, bias=False)

    def forward(self, gru_out: torch.Tensor, mask: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
        energy = torch.tanh(self.energy(gru_out))
        scores = self.v(energy).squeeze(-1)
        scores = scores + (1.0 - mask) * -1e4
        attn_weights = torch.softmax(scores, dim=1)
        context = torch.bmm(attn_weights.unsqueeze(1), gru_out).squeeze(1)
        return context, attn_weights


class ThreatBiGRU(nn.Module):
    """BiGRU temporal detector for attack sequence progression.

    Predicts the next-window anomaly score given a sequence of feature vectors.
    ONNX-compatible: uses mask-based attention instead of pack_padded_sequence.
    """

    def __init__(
        self,
        input_dim: int = 12,
        hidden_size: int = 64,
        num_layers: int = 2,
        dropout: float = 0.3,
    ) -> None:
        super().__init__()
        self.hidden_size = hidden_size

        self.input_proj = nn.Sequential(
            nn.Linear(input_dim, hidden_size),
            nn.LayerNorm(hidden_size),
            nn.ReLU(),
            nn.Dropout(dropout),
        )

        self.bigru = nn.GRU(
            input_size=hidden_size,
            hidden_size=hidden_size // 2,
            num_layers=num_layers,
            batch_first=True,
            bidirectional=True,
            dropout=dropout if num_layers > 1 else 0.0,
        )

        self.attention = AdditiveAttention(hidden_size)

        self.output_head = nn.Sequential(
            nn.Linear(hidden_size, hidden_size // 2),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_size // 2, 1),
            nn.Sigmoid(),
        )

    def forward(self, x: torch.Tensor, mask: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
        """Forward pass.

        Args:
            x: [B, T, input_dim] padded feature sequences.
            mask: [B, T] float mask (1.0 = valid, 0.0 = padding).

        Returns:
            prediction: [B, 1] next-window anomaly score estimate (0-1).
            attn_weights: [B, T] attention weights for interpretability.
        """
        projected = self.input_proj(x)
        gru_out, _ = self.bigru(projected)
        context, attn_weights = self.attention(gru_out, mask)
        prediction = self.output_head(context)
        prediction = torch.nan_to_num(prediction, nan=0.0)
        return prediction, attn_weights
