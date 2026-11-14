"""Measure an app-image and flag large identical files without deleting assets."""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path


def inspect_package(directory):
    directory = Path(directory).resolve(strict=True)
    if not directory.is_dir():
        raise ValueError("Expected an application directory")
    files = sorted(file for file in directory.rglob("*") if file.is_file())
    buckets = {"java_runtime": 0, "browser_runtime": 0, "application": 0}
    candidates = defaultdict(list)
    largest = []
    for file in files:
        relative = file.relative_to(directory).as_posix()
        size = file.stat().st_size
        bucket = ("java_runtime" if relative.startswith("runtime/") else
                  "browser_runtime" if relative.startswith("app/resources/browser-runtime/") else
                  "application")
        buckets[bucket] += size
        largest.append({"path": relative, "bytes": size})
        if size >= 1024 * 1024:
            candidates[size].append(file)
    duplicates = []
    for size, paths in sorted(candidates.items(), reverse=True):
        if len(paths) < 2:
            continue
        hashes = defaultdict(list)
        for file in paths:
            with file.open("rb") as stream:
                digest = hashlib.file_digest(stream, "sha256").hexdigest()
            hashes[digest].append(file.relative_to(directory).as_posix())
        for paths_with_same_hash in hashes.values():
            if len(paths_with_same_hash) > 1:
                duplicates.append({"bytes_each": size, "paths": paths_with_same_hash})
    return {
        "total_bytes": sum(buckets.values()),
        "file_count": len(files),
        "components": buckets,
        "largest_files": sorted(largest, key=lambda row: row["bytes"], reverse=True)[:20],
        "duplicate_files_at_least_1mib": duplicates,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--app-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = json.dumps(inspect_package(args.app_dir), indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.write_text(report, encoding="utf-8")
    else:
        print(report, end="")
