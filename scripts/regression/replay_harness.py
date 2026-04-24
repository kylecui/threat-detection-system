#!/usr/bin/env python3
"""Offline replay regression harness for threat-detection pipeline."""

import argparse
import copy
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Tuple

import psycopg2
from psycopg2.extras import RealDictCursor
import requests

try:
    from kafka import KafkaConsumer
except Exception:  # pragma: no cover - optional runtime dependency behavior
    KafkaConsumer = None


# Environment-driven config (with defaults)
KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = os.getenv("DB_PORT", "5432")
DB_NAME = os.getenv("DB_NAME", "threat_detection")
DB_USER = os.getenv("DB_USER", "threat_user")
DB_PASSWORD = os.getenv("DB_PASSWORD", "threat_password")

THREAT_ALERTS_TOPIC = "threat-alerts"
FIXTURE_DIR = Path(__file__).resolve().parent / "fixtures"


class Color:
    HEADER = "\033[95m"
    OKBLUE = "\033[94m"
    OKCYAN = "\033[96m"
    OKGREEN = "\033[92m"
    WARNING = "\033[93m"
    FAIL = "\033[91m"
    ENDC = "\033[0m"
    BOLD = "\033[1m"


def print_header(msg: str) -> None:
    print(f"\n{Color.HEADER}{Color.BOLD}{'=' * 80}{Color.ENDC}")
    print(f"{Color.HEADER}{Color.BOLD}{msg:^80}{Color.ENDC}")
    print(f"{Color.HEADER}{Color.BOLD}{'=' * 80}{Color.ENDC}\n")


def print_success(msg: str) -> None:
    print(f"{Color.OKGREEN}✓ {msg}{Color.ENDC}")


def print_error(msg: str) -> None:
    print(f"{Color.FAIL}✗ {msg}{Color.ENDC}")


def print_info(msg: str) -> None:
    print(f"{Color.OKCYAN}ℹ {msg}{Color.ENDC}")


def print_warning(msg: str) -> None:
    print(f"{Color.WARNING}⚠ {msg}{Color.ENDC}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Replay V1/V2 fixtures into data-ingestion and validate pipeline outputs."
    )
    parser.add_argument(
        "--dataset",
        choices=["v1", "v2", "all"],
        required=True,
        help="Dataset to replay: v1, v2, or all",
    )
    parser.add_argument(
        "--target",
        help="Data-ingestion base URL (e.g. http://localhost:8080)",
    )
    parser.add_argument(
        "--wait",
        type=int,
        default=240,
        help="Seconds to wait for stream processing before validation (default: 240)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Validate fixtures and plan replay only; do not send events",
    )
    return parser.parse_args()


def load_fixture(name: str) -> Dict[str, Any]:
    path = FIXTURE_DIR / name
    if not path.exists():
        raise FileNotFoundError(f"Fixture not found: {path}")
    with path.open("r", encoding="utf-8") as fh:
        data = json.load(fh)
    if not isinstance(data, dict):
        raise ValueError(f"Fixture {name} must be a JSON object")
    if "customerId" not in data or "events" not in data:
        raise ValueError(f"Fixture {name} requires customerId and events fields")
    if not isinstance(data["events"], list) or not data["events"]:
        raise ValueError(f"Fixture {name} events must be a non-empty array")
    return data


def ensure_fixture_shape(v1_data: Dict[str, Any], v2_data: Dict[str, Any]) -> None:
    for idx, item in enumerate(v1_data["events"]):
        if not isinstance(item, str):
            raise ValueError(f"v1 fixture event #{idx} must be a syslog KV string")
    for idx, item in enumerate(v2_data["events"]):
        if not isinstance(item, dict):
            raise ValueError(f"v2 fixture event #{idx} must be a JSON object")


def unique_customer_id(dataset: str) -> str:
    ts = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
    return f"regression-test-{dataset}-{ts}"


def prepare_v1_events(events: List[str], run_customer_id: str) -> List[str]:
    prepared = []
    for event in events:
        if "customerId=" in event:
            prefix, _, _ = event.partition("customerId=")
            rebuilt = prefix.rstrip(",") + f",customerId={run_customer_id}"
            prepared.append(rebuilt)
        else:
            prepared.append(f"{event},customerId={run_customer_id}")
    return prepared


def prepare_v2_events(
    events: List[Dict[str, Any]], run_customer_id: str
) -> List[Dict[str, Any]]:
    prepared = []
    for event in events:
        item = copy.deepcopy(event)
        item["customerId"] = run_customer_id
        prepared.append(item)
    return prepared


def replay_v1(target: str, events: List[str], run_customer_id: str) -> Dict[str, Any]:
    endpoint = f"{target.rstrip('/')}/api/v1/logs/ingest"
    sent = 0
    failed = 0
    failures: List[str] = []

    for idx, event in enumerate(events, start=1):
        try:
            resp = requests.post(
                endpoint,
                data=event.encode("utf-8"),
                headers={
                    "Content-Type": "text/plain",
                    "X-Customer-Id": run_customer_id,
                    "X-CustomerId": run_customer_id,
                },
                timeout=10,
            )
            if 200 <= resp.status_code < 300:
                sent += 1
            else:
                failed += 1
                failures.append(
                    f"V1 event#{idx} HTTP {resp.status_code}: {resp.text[:180]}"
                )
        except requests.RequestException as exc:
            failed += 1
            failures.append(f"V1 event#{idx} request error: {exc}")

    return {"sent": sent, "failed": failed, "failures": failures}


def replay_v2(
    target: str, events: List[Dict[str, Any]], run_customer_id: str
) -> Dict[str, Any]:
    endpoint = f"{target.rstrip('/')}/api/v1/logs/batch"
    attempts: List[Tuple[str, Dict[str, Any]]] = [
        ("events-wrapper", {"customerId": run_customer_id, "events": events}),
        ("events-only", {"events": events}),
        (
            "legacy-logs",
            {"logs": [json.dumps(item, ensure_ascii=False) for item in events]},
        ),
    ]

    errors: List[str] = []
    for mode, payload in attempts:
        try:
            resp = requests.post(
                endpoint,
                json=payload,
                headers={
                    "Content-Type": "application/json",
                    "X-Customer-Id": run_customer_id,
                    "X-CustomerId": run_customer_id,
                },
                timeout=30,
            )
            if 200 <= resp.status_code < 300:
                return {
                    "sent": len(events),
                    "failed": 0,
                    "mode": mode,
                    "status": resp.status_code,
                    "response": resp.text[:300],
                    "failures": [],
                }
            errors.append(
                f"mode={mode}, http={resp.status_code}, body={resp.text[:180]}"
            )
        except requests.RequestException as exc:
            errors.append(f"mode={mode}, request error={exc}")

    return {"sent": 0, "failed": len(events), "mode": "none", "failures": errors}


def wait_for_processing(wait_seconds: int) -> None:
    if wait_seconds <= 0:
        print_warning("Skipping wait phase because --wait <= 0")
        return
    print_info(f"Waiting {wait_seconds}s for stream processing window...")
    time.sleep(wait_seconds)


def validate_kafka(customer_id: str) -> Dict[str, Any]:
    if KafkaConsumer is None:
        return {"available": False, "reason": "kafka-python not importable"}

    group_id = f"replay-harness-{int(time.time())}"
    alerts: List[Dict[str, Any]] = []

    try:
        consumer = KafkaConsumer(
            THREAT_ALERTS_TOPIC,
            bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
            group_id=group_id,
            auto_offset_reset="earliest",
            enable_auto_commit=False,
            consumer_timeout_ms=8000,
            value_deserializer=lambda x: json.loads(x.decode("utf-8")),
        )
    except Exception as exc:
        return {"available": False, "reason": str(exc)}

    try:
        for msg in consumer:
            payload = msg.value if isinstance(msg.value, dict) else {}
            cid = payload.get("customerId") or payload.get("customer_id")
            if cid == customer_id:
                alerts.append(payload)
    finally:
        consumer.close()

    return {
        "available": True,
        "alerts": len(alerts),
        "sample": alerts[:3],
    }


def validate_db(customer_id: str) -> Dict[str, Any]:
    conn = None
    try:
        conn = psycopg2.connect(
            host=DB_HOST,
            port=DB_PORT,
            dbname=DB_NAME,
            user=DB_USER,
            password=DB_PASSWORD,
            connect_timeout=5,
        )
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute(
                """
                SELECT customer_id, attack_mac, threat_score, threat_level, created_at
                FROM threat_assessments
                WHERE customer_id = %s
                ORDER BY created_at DESC
                LIMIT 100
                """,
                (customer_id,),
            )
            rows = cur.fetchall()
    except Exception as exc:
        return {"available": False, "reason": str(exc)}
    finally:
        if conn is not None:
            conn.close()

    if not rows:
        return {
            "available": True,
            "records": 0,
            "passed": False,
            "reason": "No threat_assessments records found for test customer",
        }

    valid_scores = [r for r in rows if float(r.get("threat_score") or 0) > 0]
    valid_levels = [r for r in rows if r.get("threat_level")]
    passed = len(valid_scores) > 0 and len(valid_levels) > 0

    return {
        "available": True,
        "records": len(rows),
        "positive_scores": len(valid_scores),
        "with_levels": len(valid_levels),
        "passed": passed,
        "sample": rows[:3],
    }


def report(
    args: argparse.Namespace,
    run_customer: str,
    replay_stats: Dict[str, Dict[str, Any]],
    kafka_result: Dict[str, Any],
    db_result: Dict[str, Any],
) -> int:
    print_header("Regression Replay Summary")
    print_info(f"Dataset: {args.dataset}")
    print_info(f"Run customerId: {run_customer}")
    if args.target:
        print_info(f"Target: {args.target}")

    total_sent = sum(item.get("sent", 0) for item in replay_stats.values())
    total_failed = sum(item.get("failed", 0) for item in replay_stats.values())

    print_info(f"Replay sent: {total_sent}")
    if total_failed == 0:
        print_success("Replay phase completed without send failures")
    else:
        print_error(f"Replay failures: {total_failed}")

    for name, stats in replay_stats.items():
        if stats.get("failures"):
            print_warning(f"{name} failures ({len(stats['failures'])}):")
            for item in stats["failures"][:5]:
                print(f"  - {item}")

    if not kafka_result.get("available"):
        print_warning(f"Kafka validation skipped: {kafka_result.get('reason')}")
    else:
        print_info(
            f"Kafka alerts found for run customer: {kafka_result.get('alerts', 0)}"
        )
        if kafka_result.get("sample"):
            print_info(
                "Kafka sample alert: "
                + json.dumps(kafka_result["sample"][0], ensure_ascii=False)[:220]
            )

    if not db_result.get("available"):
        print_warning(f"DB validation skipped: {db_result.get('reason')}")
        db_passed = False
    else:
        print_info(f"DB records found: {db_result.get('records', 0)}")
        print_info(
            f"DB records with threat_score > 0: {db_result.get('positive_scores', 0)}"
        )
        print_info(f"DB records with threat_level: {db_result.get('with_levels', 0)}")
        db_passed = bool(db_result.get("passed"))

    replay_passed = total_failed == 0
    final_passed = replay_passed and db_passed

    if final_passed:
        print_success("REGRESSION HARNESS PASS")
        return 0

    print_error("REGRESSION HARNESS FAIL")
    if db_result.get("available") and not db_result.get("passed"):
        print_error(str(db_result.get("reason", "DB validation failed")))
    return 1


def main() -> int:
    args = parse_args()
    if not args.dry_run and not args.target:
        print_error("--target is required unless --dry-run is set")
        return 1

    print_header("Offline Replay Regression Harness")
    print_info(f"Fixture directory: {FIXTURE_DIR}")

    v1_data = load_fixture("v1_sample_events.json")
    v2_data = load_fixture("v2_sample_events.json")
    ensure_fixture_shape(v1_data, v2_data)
    print_success("Fixture validation passed")
    print_info(f"V1 events: {len(v1_data['events'])}")
    print_info(f"V2 events: {len(v2_data['events'])}")

    run_customer = unique_customer_id(args.dataset)
    replay_stats: Dict[str, Dict[str, Any]] = {}

    if args.dry_run:
        print_warning("Dry-run mode: no events will be sent")
        print_info(f"Planned run customerId: {run_customer}")
        print_success("Dry-run completed")
        return 0

    if args.dataset in ("v1", "all"):
        print_header("Replay V1")
        v1_events = prepare_v1_events(v1_data["events"], run_customer)
        replay_stats["v1"] = replay_v1(args.target, v1_events, run_customer)
        if replay_stats["v1"].get("failed", 0) == 0:
            print_success(f"V1 replay sent {replay_stats['v1']['sent']} events")
        else:
            print_error(
                f"V1 replay failed: {replay_stats['v1']['failed']} / {len(v1_events)}"
            )

    if args.dataset in ("v2", "all"):
        print_header("Replay V2")
        v2_events = prepare_v2_events(v2_data["events"], run_customer)
        replay_stats["v2"] = replay_v2(args.target, v2_events, run_customer)
        if replay_stats["v2"].get("failed", 0) == 0:
            print_success(
                f"V2 replay sent {replay_stats['v2']['sent']} events "
                f"(mode={replay_stats['v2'].get('mode')})"
            )
        else:
            print_error(
                f"V2 replay failed: {replay_stats['v2']['failed']} / {len(v2_events)}"
            )

    wait_for_processing(args.wait)

    print_header("Validate Outputs")
    kafka_result = validate_kafka(run_customer)
    db_result = validate_db(run_customer)

    return report(args, run_customer, replay_stats, kafka_result, db_result)


if __name__ == "__main__":
    sys.exit(main())
