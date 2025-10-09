#!/usr/bin/env python3
"""Utility for replaying sample syslog payloads against the data-ingestion service."""

import argparse
import json
import sys
import time
from pathlib import Path
from typing import Iterable, Optional, Tuple

import requests

DEFAULT_SERVICE_URL = "http://localhost:8080/api/v1/logs/ingest"


def repo_root() -> Path:
    return Path(__file__).resolve().parents[1]


SAMPLE_FILES = {
    "attack": repo_root() / "tmp" / "2024-04-25.07.56.log",
    "status": repo_root() / "tmp" / "2024-04-25.08.21.log",
}


def extract_message(raw_line: str) -> str:
    try:
        payload = json.loads(raw_line)
    except json.JSONDecodeError:
        return raw_line.strip()

    if not isinstance(payload, dict):
        return raw_line.strip()

    for path in (
        ("message",),
        ("event", "original"),
        ("log", "message"),
    ):
        candidate = _lookup(payload, path)
        if isinstance(candidate, str) and candidate.strip():
            return candidate.strip()

    return raw_line.strip()


def _lookup(container: dict, path: Tuple[str, ...]) -> Optional[str]:
    current = container
    for part in path:
        if isinstance(current, dict) and part in current:
            current = current[part]
        else:
            return None
    return current if isinstance(current, str) else None


def iter_messages(file_path: Path, max_records: Optional[int]) -> Iterable[Tuple[int, str]]:
    emitted = 0
    with file_path.open(encoding="utf-8") as handle:
        for index, raw in enumerate(handle, 1):
            if not raw.strip():
                continue
            emitted += 1
            yield index, extract_message(raw)
            if max_records is not None and emitted >= max_records:
                break


def send_log(session: requests.Session, service_url: str, message: str) -> bool:
    data = (message or "").strip().encode("utf-8")
    try:
        response = session.post(
            service_url,
            data=data,
            headers={"Content-Type": "text/plain; charset=utf-8"},
            timeout=10,
        )
    except requests.RequestException as exc:
        print(f"Request error: {exc}")
        return False

    if response.status_code == 200:
        return True

    print(f"Unexpected HTTP {response.status_code}: {response.text}")
    return False


def process_file(
    session: requests.Session,
    file_path: Path,
    service_url: str,
    delay: float,
    dry_run: bool,
    max_records: Optional[int],
) -> Tuple[int, int]:
    success = 0
    errors = 0

    for line_no, message in iter_messages(file_path, max_records):
        preview = message.replace("\n", " ")[:100]
        print(f"[{file_path.name}:{line_no}] {preview}")

        if dry_run:
            success += 1
        else:
            if send_log(session, service_url, message):
                success += 1
            else:
                errors += 1

        if delay > 0:
            time.sleep(delay)

    return success, errors


def resolve_targets(files: Iterable[str], include_samples: Iterable[str]) -> Iterable[Path]:
    seen = set()

    for label in include_samples:
        sample_path = SAMPLE_FILES.get(label)
        if sample_path is None:
            raise ValueError(f"Unknown sample alias '{label}'. Valid options: {', '.join(SAMPLE_FILES)}")
        seen.add(sample_path.resolve())
        yield sample_path

    for item in files:
        candidate = Path(item).expanduser().resolve()
        if candidate in seen:
            continue
        seen.add(candidate)
        yield candidate


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Replay syslog payloads through the data-ingestion HTTP API",
    )
    parser.add_argument(
        "paths",
        nargs="*",
        help="Log files to replay (JSON lines with a message field)",
    )
    parser.add_argument(
        "--sample",
        choices=sorted(SAMPLE_FILES),
        action="append",
        help="Replay one of the bundled sample captures (can be provided multiple times)",
    )
    parser.add_argument(
        "--url",
        default=DEFAULT_SERVICE_URL,
        help=f"Data ingestion endpoint (default: {DEFAULT_SERVICE_URL})",
    )
    parser.add_argument(
        "--delay",
        type=float,
        default=0.1,
        help="Delay in seconds between requests",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Parse and display messages without sending HTTP requests",
    )
    parser.add_argument(
        "--max-records",
        type=int,
        help="Limit the number of entries replayed from each file",
    )

    args = parser.parse_args()

    sample_labels = args.sample or []
    targets = list(resolve_targets(args.paths, sample_labels))

    if not targets:
        parser.error("No input files supplied. Provide paths or use --sample {attack,status}.")

    session = requests.Session()
    total_ok = 0
    total_err = 0

    for path in targets:
        if not path.exists():
            print(f"Skipped missing file: {path}")
            continue

        print(f"Processing {path} -> {args.url}")
        ok, err = process_file(
            session=session,
            file_path=path,
            service_url=args.url,
            delay=args.delay,
            dry_run=args.dry_run,
            max_records=args.max_records,
        )
        total_ok += ok
        total_err += err

    print("-" * 60)
    print(f"Delivered messages: {total_ok}")
    print(f"Failed deliveries: {total_err}")
    print(f"Mode: {'dry-run' if args.dry_run else 'live-send'}")

    sys.exit(0 if total_err == 0 else 1)


if __name__ == "__main__":
    main()