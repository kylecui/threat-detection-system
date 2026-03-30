#!/usr/bin/env python3
"""
V1 Historical Log Ingestion Script

Reads old V1 syslog JSON-line files from the Processed directory,
extracts the KV 'message' field, and POSTs batches to the
data-ingestion service's /api/v1/logs/batch endpoint.

Usage:
    python3 scripts/tools/ingest_v1_historical_logs.py \
        --log-dir /mnt/d/MyWorkSpaces/jzzn_Cloud/old/logstash/logs/Processed \
        --api-url http://10.174.1.229:30080 \
        --batch-size 200 \
        --workers 4

The script:
  1. Reads each .log file (JSON-lines format from Logstash)
  2. Extracts the 'message' field (syslog KV pairs)
  3. Strips trailing whitespace/newlines from message
  4. Filters to log_type=1 (attack events) by default
  5. POSTs batches to /api/v1/logs/batch
  6. Reports progress and final statistics
"""

import argparse
import json
import os
import sys
import time
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("v1-ingest")


def create_session() -> requests.Session:
    session = requests.Session()
    retry = Retry(
        total=3,
        backoff_factor=1.0,
        status_forcelist=[429, 500, 502, 503, 504],
        allowed_methods=["POST"],
    )
    adapter = HTTPAdapter(
        max_retries=retry,
        pool_connections=10,
        pool_maxsize=20,
    )
    session.mount("http://", adapter)
    session.mount("https://", adapter)
    return session


def login(api_url: str, session: requests.Session) -> str:
    """Login and return JWT token."""
    resp = session.post(
        f"{api_url}/api/v1/auth/login",
        json={"username": "admin", "password": "admin123"},
        timeout=10,
    )
    resp.raise_for_status()
    token = resp.json()["token"]
    log.info("Authenticated as admin")
    return token


def read_log_files(log_dir: str, include_status: bool = False):
    """
    Yield (filename, kv_message) tuples from all .log files.
    Each file contains one or more JSON lines from Logstash.
    """
    log_path = Path(log_dir)
    files = sorted(log_path.glob("*.log"))
    log.info("Found %d log files in %s", len(files), log_dir)

    for fpath in files:
        with open(fpath, "r", encoding="utf-8", errors="replace") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    data = json.loads(line)
                except json.JSONDecodeError:
                    continue

                msg = data.get("message", "")
                if not msg:
                    # Fallback: try event.original
                    msg = data.get("event", {}).get("original", "")
                if not msg:
                    continue

                # Strip trailing newlines and whitespace
                msg = msg.strip()

                # Must be a valid syslog KV message (contains syslog_version= and log_type=)
                if "syslog_version=" not in msg or "log_type=" not in msg:
                    continue

                # Filter: only attack events (log_type=1) unless include_status
                if not include_status and "log_type=1" not in msg:
                    continue

                yield (fpath.name, msg)


def send_batch(
    session: requests.Session,
    api_url: str,
    token: str,
    batch: list[str],
    batch_num: int,
) -> dict:
    """Send a batch of KV log strings to the batch API."""
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}",
    }
    try:
        resp = session.post(
            f"{api_url}/api/v1/logs/batch",
            json={"logs": batch},
            headers=headers,
            timeout=30,
        )
        if resp.status_code == 200:
            result = resp.json()
            return {
                "batch": batch_num,
                "total": result.get("totalCount", len(batch)),
                "success": result.get("successCount", 0),
                "errors": result.get("errorCount", 0),
                "time_ms": result.get("processingTimeMs", 0),
            }
        else:
            return {
                "batch": batch_num,
                "total": len(batch),
                "success": 0,
                "errors": len(batch),
                "time_ms": 0,
                "http_error": resp.status_code,
            }
    except requests.RequestException as e:
        return {
            "batch": batch_num,
            "total": len(batch),
            "success": 0,
            "errors": len(batch),
            "time_ms": 0,
            "exception": str(e),
        }


def main():
    parser = argparse.ArgumentParser(description="Ingest V1 historical logs")
    parser.add_argument(
        "--log-dir",
        default="/mnt/d/MyWorkSpaces/jzzn_Cloud/old/logstash/logs/Processed",
        help="Directory containing .log files",
    )
    parser.add_argument(
        "--api-url",
        default="http://10.174.1.229:30080",
        help="API Gateway base URL",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=200,
        help="Number of log lines per batch request (max 1000)",
    )
    parser.add_argument(
        "--workers",
        type=int,
        default=2,
        help="Number of parallel HTTP workers",
    )
    parser.add_argument(
        "--sleep",
        type=float,
        default=0.1,
        help="Sleep seconds between batch submissions (rate-limit safety)",
    )
    parser.add_argument(
        "--include-status",
        action="store_true",
        help="Include log_type=2 (status) events as well",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Count logs without sending",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Limit total events to ingest (0 = unlimited)",
    )
    args = parser.parse_args()

    if not os.path.isdir(args.log_dir):
        log.error("Log directory not found: %s", args.log_dir)
        sys.exit(1)

    # ── Collect all messages ──
    log.info("Reading log files from: %s", args.log_dir)
    messages = []
    file_counts = {}
    for fname, msg in read_log_files(args.log_dir, args.include_status):
        messages.append(msg)
        file_counts[fname] = file_counts.get(fname, 0) + 1
        if args.limit and len(messages) >= args.limit:
            break

    log.info(
        "Collected %d events from %d files", len(messages), len(file_counts)
    )

    if args.dry_run:
        log.info("DRY RUN — would send %d events in %d batches of %d",
                 len(messages),
                 (len(messages) + args.batch_size - 1) // args.batch_size,
                 args.batch_size)
        # Show sample
        if messages:
            log.info("Sample event: %s", messages[0][:200])
        return

    if not messages:
        log.warning("No events to ingest.")
        return

    # ── Authenticate ──
    session = create_session()
    token = login(args.api_url, session)

    # ── Build batches ──
    batches = []
    for i in range(0, len(messages), args.batch_size):
        batches.append(messages[i : i + args.batch_size])
    log.info(
        "Sending %d events in %d batches (size=%d, workers=%d)",
        len(messages),
        len(batches),
        args.batch_size,
        args.workers,
    )

    # ── Send with controlled concurrency ──
    total_success = 0
    total_errors = 0
    total_time_ms = 0
    consecutive_errors = 0
    start = time.time()

    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        # Submit in chunks to avoid overwhelming the gateway
        chunk_size = args.workers * 5  # submit 10 batches at a time for 2 workers
        batch_idx = 0
        completed = 0

        while batch_idx < len(batches):
            chunk_end = min(batch_idx + chunk_size, len(batches))
            futures = {}
            for i in range(batch_idx, chunk_end):
                futures[pool.submit(send_batch, session, args.api_url, token, batches[i], i)] = i
                time.sleep(args.sleep)  # small delay between submissions

            for future in as_completed(futures):
                result = future.result()
                completed += 1
                total_success += result["success"]
                total_errors += result["errors"]
                total_time_ms += result["time_ms"]

                if "http_error" in result:
                    consecutive_errors += 1
                    log.warning(
                        "Batch %d: HTTP %d (%d events lost)",
                        result["batch"],
                        result["http_error"],
                        result["errors"],
                    )
                    # If we get 401/403, refresh token
                    if result["http_error"] in (401, 403):
                        log.info("Refreshing JWT token...")
                        token = login(args.api_url, session)
                    # Back off on rate limit
                    if result["http_error"] == 429:
                        log.info("Rate limited, sleeping 5s...")
                        time.sleep(5)
                elif "exception" in result:
                    consecutive_errors += 1
                    log.warning(
                        "Batch %d: %s (%d events lost)",
                        result["batch"],
                        result["exception"],
                        result["errors"],
                    )
                else:
                    consecutive_errors = 0

                # Abort if too many consecutive errors
                if consecutive_errors >= 20:
                    log.error("Too many consecutive errors (%d), aborting!", consecutive_errors)
                    break

                # Progress every 50 batches
                if completed % 50 == 0 or completed == len(batches):
                    elapsed = time.time() - start
                    rate = total_success / elapsed if elapsed > 0 else 0
                    log.info(
                        "Progress: %d/%d batches | %d OK / %d ERR | %.0f events/sec",
                        completed,
                        len(batches),
                        total_success,
                        total_errors,
                        rate,
                    )

            if consecutive_errors >= 20:
                break

            batch_idx = chunk_end

    elapsed = time.time() - start
    rate = total_success / elapsed if elapsed > 0 else 0

    log.info("=" * 60)
    log.info("INGESTION COMPLETE")
    log.info("  Total events:   %d", len(messages))
    log.info("  Successful:     %d", total_success)
    log.info("  Failed:         %d", total_errors)
    log.info("  Elapsed:        %.1f seconds", elapsed)
    log.info("  Throughput:     %.0f events/sec", rate)
    log.info("  Server time:    %d ms total", total_time_ms)
    log.info("  Success rate:   %.1f%%", 100 * total_success / max(len(messages), 1))
    log.info("=" * 60)


if __name__ == "__main__":
    main()
