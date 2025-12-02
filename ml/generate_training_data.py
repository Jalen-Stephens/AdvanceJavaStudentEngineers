"""Generate training features that exactly mirror the Java OpenCV pipeline.

Reads a train.csv file with rows shaped like:
    index,train_data/image_id.jpg,label

Images live under an images directory (default: /Users/ikeschmidt/Desktop/Image_Data/train_data).
Outputs a CSV where each row is the 12-feature vector used by the Java model plus the label:
laplacian_variance, noise_stddev, edge_density, high_low_freq_ratio, saturation_entropy,
width, height, aspect_ratio, c2pa_has_manifest, c2pa_manifest_count,
c2pa_claim_generator_is_ai, c2pa_error_flag, label
"""

from __future__ import annotations

import argparse
import csv
import os
from typing import Iterable, List, Sequence

import cv2
import numpy as np


# ---- Feature calculations (kept 1:1 with FeatureExtractor.java) ----

def laplacian_variance(img: np.ndarray) -> float:
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    lap = cv2.Laplacian(gray, cv2.CV_64F)
    _, std = cv2.meanStdDev(lap)
    return float(std[0][0] ** 2)


def noise_estimate(img: np.ndarray) -> float:
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (7, 7), 0)
    diff = cv2.absdiff(gray, blur)
    _, std = cv2.meanStdDev(diff)
    return float(std[0][0])


def edge_density(img: np.ndarray) -> float:
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    edges = cv2.Canny(gray, 100, 200)
    total = float(edges.shape[0] * edges.shape[1])
    non_zero = float(cv2.countNonZero(edges))
    return 0.0 if total == 0 else non_zero / total


def high_low_freq_ratio(img: np.ndarray) -> float:
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY).astype(np.float32)
    complex_img = cv2.merge([gray, np.zeros_like(gray)])
    complex_img = cv2.dft(complex_img)
    real, imag = cv2.split(complex_img)
    mag = cv2.magnitude(real, imag)

    cx = mag.shape[1] // 2
    cy = mag.shape[0] // 2
    r_min = min(cx, cy)
    rlow = r_min * 0.25
    rhigh = r_min * 0.75

    ys, xs = np.indices(mag.shape)
    dist = np.sqrt((xs - cx) ** 2 + (ys - cy) ** 2)

    low_mask = dist < rlow
    high_mask = dist > rhigh

    low = float(mag[low_mask].sum())
    high = float(mag[high_mask].sum())
    return 0.0 if low == 0 else high / low


def saturation_entropy(img: np.ndarray) -> float:
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    sat = hsv[:, :, 1]
    hist = cv2.calcHist([sat], [0], None, [64], [0, 256])
    hist = cv2.normalize(hist, hist, alpha=1.0, beta=0.0, norm_type=cv2.NORM_L1)
    hist = hist.flatten()

    mask = hist > 1e-8
    safe = hist[mask]
    return float(-np.sum(safe * np.log(safe)))


def aspect_ratio(width: int, height: int) -> float:
    return 0.0 if height == 0 else float(width) / float(height)


def extract_features(img: np.ndarray) -> List[float]:
    if img is None:
        # Mirror FeatureExtractor.generateEmpty: zeros for OpenCV features/C2PA.
        return [0.0] * 12

    height, width = img.shape[:2]
    return [
        laplacian_variance(img),
        noise_estimate(img),
        edge_density(img),
        high_low_freq_ratio(img),
        saturation_entropy(img),
        float(width),
        float(height),
        aspect_ratio(width, height),
        0.0,  # c2pa_has_manifest
        0.0,  # c2pa_manifest_count
        0.0,  # c2pa_claim_generator_is_ai
        0.0,  # c2pa_error_flag
    ]


# ---- Data ingestion and writing ----

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


def resolve_image_path(path_value: str, images_dir: str) -> str:
    """Resolve the CSV path to an on-disk file, handling common dataset layouts."""
    if os.path.isabs(path_value):
        return path_value

    candidate = os.path.join(images_dir, path_value)
    if os.path.exists(candidate):
        return candidate

    # If the CSV path already includes the folder name (e.g., train_data/foo.jpg)
    # but images_dir also points to train_data, strip the first component.
    parts = path_value.split("/", 1)
    if len(parts) == 2:
        alt = os.path.join(images_dir, parts[1])
        if os.path.exists(alt):
            return alt

    return candidate


def read_rows(csv_path: str) -> Iterable[Sequence[str]]:
    with open(csv_path, newline="") as f:
        reader = csv.reader(f)
        for row in reader:
            if not row or len(row) < 3:
                continue
            # Skip header-like first row
            if row[0].lower() in {"index", "id", "idx"}:
                continue
            yield row


def print_progress(current: int, total: int, width: int = 40) -> None:
    """Lightweight terminal progress bar."""
    filled = int(width * current / max(total, 1))
    bar = "#" * filled + "-" * (width - filled)
    end = "\n" if current >= total else "\r"
    print(f"[{bar}] {current}/{total}", end=end, flush=True)


def build_dataset(csv_path: str, images_dir: str, show_progress: bool = True) -> List[List[float]]:
    rows = list(read_rows(csv_path))
    total = len(rows)

    records: List[List[float]] = []
    for idx, row in enumerate(rows, start=1):
        _, image_rel, label_str = row[0].strip(), row[1].strip(), row[2].strip()
        image_path = resolve_image_path(image_rel, images_dir)
        img = cv2.imread(image_path)
        feats = extract_features(img)
        try:
            label = float(label_str)
        except ValueError:
            label = 0.0
        feats.append(label)
        records.append(feats)

        if show_progress:
            print_progress(idx, total)

    if show_progress and total == 0:
        print("No rows found in CSV.")
    return records


def write_output(rows: List[List[float]], output_path: str) -> None:
    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
    with open(output_path, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(FEATURE_HEADER)
        writer.writerows(rows)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate training data that matches Java OpenCV features.")
    parser.add_argument(
        "--csv",
        default="train.csv",
        help="Input CSV with rows of index,image_path,label (default: train.csv)",
    )
    parser.add_argument(
        "--images-dir",
        default="/Users/ikeschmidt/Desktop/Image_Data/train_data",
        help="Directory containing the training images (default: %(default)s)",
    )
    parser.add_argument(
        "--output",
        default="training_features.csv",
        help="Where to write the generated feature CSV (default: training_features.csv)",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    rows = build_dataset(args.csv, args.images_dir, show_progress=True)
    write_output(rows, args.output)
    print(f"Wrote {len(rows)} rows to {args.output}")


if __name__ == "__main__":
    main()
