"""Generate testing features from unlabeled folders (real vs fake).

Assumes the test directory contains subfolders:
  - real/  (real photos)
  - fake/  (AI-generated photos)

Outputs a CSV with the exact same columns as training_features.csv:
laplacian_variance, noise_stddev, edge_density, high_low_freq_ratio, saturation_entropy,
width, height, aspect_ratio, c2pa_has_manifest, c2pa_manifest_count,
c2pa_claim_generator_is_ai, c2pa_error_flag, label
"""

from __future__ import annotations

import argparse
import csv
import os
from typing import Dict, Iterable, List, Sequence, Tuple

import cv2
import numpy as np


# ---- Feature calculations (mirrors FeatureExtractor.java) ----

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
        0.0,
        0.0,
        0.0,
        0.0,
    ]


# ---- Dataset assembly ----

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


def print_progress(current: int, total: int, width: int = 40) -> None:
    filled = int(width * current / max(total, 1))
    bar = "#" * filled + "-" * (width - filled)
    end = "\n" if current >= total else "\r"
    print(f"[{bar}] {current}/{total}", end=end, flush=True)


def collect_image_paths(images_dir: str, label_map: Dict[str, float]) -> List[Tuple[str, float]]:
    paths: List[Tuple[str, float]] = []
    for folder, label in label_map.items():
        base = os.path.join(images_dir, folder)
        if not os.path.isdir(base):
            continue
        for root, _, files in os.walk(base):
            for name in files:
                if name.startswith("."):
                    continue
                path = os.path.join(root, name)
                paths.append((path, label))
    # deterministic ordering
    paths.sort(key=lambda x: x[0])
    return paths


def build_dataset(images_dir: str, label_map: Dict[str, float], show_progress: bool = True) -> List[List[float]]:
    items = collect_image_paths(images_dir, label_map)
    total = len(items)
    records: List[List[float]] = []

    for idx, (path, label) in enumerate(items, start=1):
        img = cv2.imread(path)
        feats = extract_features(img)
        feats.append(float(label))
        records.append(feats)
        if show_progress:
            print_progress(idx, total)

    if show_progress and total == 0:
        print("No images found under provided directory.")
    return records


def write_output(rows: Sequence[Sequence[float]], output_path: str) -> None:
    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
    with open(output_path, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(FEATURE_HEADER)
        writer.writerows(rows)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate testing feature CSV from real/ and fake/ subfolders.")
    parser.add_argument(
        "--images-dir",
        default="/Users/ikeschmidt/Desktop/Image_Data/test",
        help="Directory containing real/ and fake/ folders (default: %(default)s)",
    )
    parser.add_argument(
        "--output",
        default="testing_features.csv",
        help="Where to write the generated feature CSV (default: testing_features.csv)",
    )
    parser.add_argument(
        "--real-label",
        type=float,
        default=0.0,
        help="Numeric label for real images (default: 0.0)",
    )
    parser.add_argument(
        "--fake-label",
        type=float,
        default=1.0,
        help="Numeric label for fake images (default: 1.0)",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    label_map = {"real": args.real_label, "fake": args.fake_label}
    rows = build_dataset(args.images_dir, label_map, show_progress=True)
    write_output(rows, args.output)
    print(f"Wrote {len(rows)} rows to {args.output}")


if __name__ == "__main__":
    main()
