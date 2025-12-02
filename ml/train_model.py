"""Train a logistic regression model matching the Java LogisticRegressionService.

Reads training_features.csv and testing_features.csv (same header/columns as the generators),
filters out empty/corrupt rows (all-zero feature vectors), trains via gradient descent,
evaluates on the test set, and writes model.json compatible with ModelLoader.

Default paths:
  --train training_features.csv
  --test  testing_features.csv
  --output src/main/resources/model.json
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import os
from typing import List, Tuple

import numpy as np


FEATURE_HEADER = [
    "laplacian_variance",
    "noise_stddev",
    "edge_density",
    "high_low_freq_ratio",
    "saturation_entropy",
    "width",
    "height",
    "aspect_ratio",
    "c2pa_has_manifest",
    "c2pa_manifest_count",
    "c2pa_claim_generator_is_ai",
    "c2pa_error_flag",
    "label",
]


def parse_csv(path: str) -> Tuple[np.ndarray, np.ndarray]:
    """Load features/labels, skipping empty lines and zeroed feature vectors."""
    X: List[List[float]] = []
    y: List[float] = []

    with open(path, newline="") as f:
        reader = csv.reader(f)
        header = next(reader, None)
        # If header present and matches expected, skip it; otherwise treat as data.
        if header and header == FEATURE_HEADER:
            pass
        else:
            if header:
                row = header
            else:
                row = None
            if row:
                reader = (r for r in [row, *reader])

        for row in reader:
            if not row or len(row) < 13:
                continue
            try:
                feats = [float(v) for v in row[:-1]]
                label = float(row[-1])
            except ValueError:
                continue

            # Skip fully zero feature rows (likely empty/corrupt).
            if all(v == 0.0 for v in feats):
                continue

            X.append(feats)
            y.append(label)

    if not X:
        raise ValueError(f"No usable rows found in {path}")

    return np.array(X, dtype=np.float64), np.array(y, dtype=np.float64)


def sigmoid(z: np.ndarray) -> np.ndarray:
    # Stable sigmoid to match Java implementation semantics.
    out = np.empty_like(z, dtype=np.float64)
    pos_mask = z >= 0
    neg_mask = ~pos_mask
    out[pos_mask] = 1.0 / (1.0 + np.exp(-z[pos_mask]))
    exp_z = np.exp(z[neg_mask])
    out[neg_mask] = exp_z / (1.0 + exp_z)
    return out


def train_logistic_regression(
    X: np.ndarray,
    y: np.ndarray,
    lr: float = 0.01,
    epochs: int = 2000,
    l2: float = 0.0,
) -> Tuple[np.ndarray, float]:
    n_samples, n_features = X.shape
    w = np.zeros(n_features, dtype=np.float64)
    b = 0.0

    for epoch in range(epochs):
        z = X.dot(w) + b
        preds = sigmoid(z)
        errors = preds - y

        grad_w = (X.T.dot(errors) / n_samples) + l2 * w
        grad_b = float(errors.mean())

        w -= lr * grad_w
        b -= lr * grad_b

    return w, b


def evaluate(X: np.ndarray, y: np.ndarray, w: np.ndarray, b: float) -> Tuple[float, float]:
    z = X.dot(w) + b
    preds = sigmoid(z)
    # Log loss (avoid log(0))
    eps = 1e-9
    loss = -np.mean(y * np.log(preds + eps) + (1 - y) * np.log(1 - preds + eps))
    acc = float(((preds >= 0.5) == (y >= 0.5)).mean())
    return acc, loss


def write_model(weights: np.ndarray, bias: float, output: str, version: str = "v1") -> None:
    os.makedirs(os.path.dirname(output) or ".", exist_ok=True)
    data = {
        "type": "logistic_regression",
        "weights": weights.tolist(),
        "bias": float(bias),
        "version": version,
    }
    with open(output, "w") as f:
        json.dump(data, f, indent=4)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train logistic regression to mirror the Java model.")
    parser.add_argument("--train", default="training_features.csv", help="Path to training features CSV.")
    parser.add_argument("--test", default="testing_features.csv", help="Path to testing features CSV.")
    parser.add_argument("--output", default="src/main/resources/model.json",
                        help="Where to write the trained model JSON.")
    parser.add_argument("--lr", type=float, default=0.01, help="Learning rate.")
    parser.add_argument("--epochs", type=int, default=2000, help="Training epochs.")
    parser.add_argument("--l2", type=float, default=0.0, help="L2 regularization strength.")
    parser.add_argument("--version", default="v1", help="Model version tag to save.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    X_train, y_train = parse_csv(args.train)
    X_test, y_test = parse_csv(args.test)

    if X_train.shape[1] != X_test.shape[1]:
        raise ValueError(f"Feature size mismatch: train has {X_train.shape[1]}, test has {X_test.shape[1]}")

    w, b = train_logistic_regression(X_train, y_train, lr=args.lr, epochs=args.epochs, l2=args.l2)

    train_acc, train_loss = evaluate(X_train, y_train, w, b)
    test_acc, test_loss = evaluate(X_test, y_test, w, b)

    print(f"Train loss: {train_loss:.4f}, acc: {train_acc:.4f}")
    print(f"Test  loss: {test_loss:.4f}, acc: {test_acc:.4f}")
    print(f"Saving model to {args.output}")

    write_model(w, b, args.output, version=args.version)


if __name__ == "__main__":
    main()
