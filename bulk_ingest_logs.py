#!/usr/bin/env python3
"""
Bulk Log Ingestion Script
Processes all log files in tmp/real_test_logs directory and sends them to data-ingestion service
"""

import os
import json
import requests
import time
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

# Configuration
API_BASE_URL = "http://localhost:8080"
LOG_FILES_DIR = "/home/kylecui/threat-detection-system/tmp/real_test_logs"
BATCH_SIZE = 25  # Reduced batch size to prevent long-running connections
MAX_WORKERS = 4  # Concurrent API calls
CONNECTION_REFRESH_INTERVAL = 1000  # Refresh connection pool every 1000 requests

# Create a session with connection pooling and retry strategy
def create_session():
    """Create a requests session with proper connection pooling and retries"""
    session = requests.Session()

    # Configure retry strategy
    retry_strategy = Retry(
        total=3,
        backoff_factor=0.5,
        status_forcelist=[429, 500, 502, 503, 504],
    )

    # Configure connection pooling
    adapter = HTTPAdapter(
        max_retries=retry_strategy,
        pool_connections=10,  # Number of connection pools
        pool_maxsize=20,      # Max connections per pool
        pool_block=False      # Don't block when pool is full
    )

    session.mount("http://", adapter)
    session.mount("https://", adapter)

    return session

# Global session for connection reuse
api_session = create_session()
request_counter = 0  # Track total requests for connection pool management

def check_service_health():
    """Check if data-ingestion service is healthy"""
    try:
        response = api_session.get(f"{API_BASE_URL}/actuator/health", timeout=10)
        return response.status_code == 200 and response.json().get('status') == 'UP'
    except Exception as e:
        print(f"❌ Service health check failed: {e}")
        return False

def extract_syslog_content(log_line):
    """Extract syslog content from JSON log line"""
    try:
        log_data = json.loads(log_line.strip())

        # Try different possible fields for syslog content
        syslog_content = None

        # Priority: event.original
        if 'event' in log_data and 'original' in log_data['event']:
            syslog_content = log_data['event']['original']
        # Fallback: message field
        elif 'message' in log_data:
            syslog_content = log_data['message']
        # Fallback: log.syslog.original
        elif 'log' in log_data and 'syslog' in log_data['log'] and 'original' in log_data['log']['syslog']:
            syslog_content = log_data['log']['syslog']['original']

        return syslog_content

    except json.JSONDecodeError:
        return None

def send_log_to_api(json_log_line):
    """Send a single JSON log line to the API with retry logic"""
    global request_counter, api_session
    max_retries = 3

    for attempt in range(max_retries):
        try:
            # Refresh connection pool periodically to prevent stale connections
            request_counter += 1
            if request_counter % CONNECTION_REFRESH_INTERVAL == 0:
                print(f"🔄 Refreshing connection pool after {request_counter} requests...")
                api_session.close()
                api_session = create_session()

            response = api_session.post(
                f"{API_BASE_URL}/api/v1/logs/ingest",
                data=json_log_line,
                headers={'Content-Type': 'text/plain'},
                timeout=30
            )

            if response.status_code == 200:
                return True, json_log_line[:100] + "..."
            else:
                # Server errors that might be transient
                if response.status_code >= 500 and attempt < max_retries - 1:
                    wait_time = 0.5 * (2 ** attempt)  # Exponential backoff
                    time.sleep(wait_time)
                    continue
                return False, f"HTTP {response.status_code}: {json_log_line[:100]}..."

        except requests.exceptions.ConnectionError as e:
            if "Connection reset by peer" in str(e) and attempt < max_retries - 1:
                # Connection reset - wait and retry with fresh connection
                wait_time = 1.0 * (2 ** attempt)
                print(f"🔄 Connection reset detected, retrying in {wait_time:.1f}s (attempt {attempt + 1}/{max_retries})")
                time.sleep(wait_time)
                # Force connection refresh
                api_session.close()
                api_session = create_session()
                continue
            return False, f"Connection error: {e}"
        except Exception as e:
            return False, f"Error: {e}"

    return False, f"Failed after {max_retries} attempts: {json_log_line[:100]}..."

def process_log_file(file_path):
    """Process a single log file and return results"""
    results = {
        'file': file_path.name,
        'total_logs': 0,
        'valid_logs': 0,
        'sent_logs': 0,
        'failed_logs': 0,
        'errors': []
    }

    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()

        results['total_logs'] = len(lines)

        # Process in batches
        for i in range(0, len(lines), BATCH_SIZE):
            batch = lines[i:i + BATCH_SIZE]
            batch_results = []

            # Filter valid JSON log lines (those with dev_serial)
            json_batch = []
            for line in batch:
                syslog_content = extract_syslog_content(line)
                if syslog_content and 'dev_serial=' in syslog_content:
                    json_batch.append(line.strip())  # Send the full JSON line
                    results['valid_logs'] += 1

            # Send batch to API concurrently
            with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
                future_to_log = {
                    executor.submit(send_log_to_api, json_line): json_line[:50] + "..."
                    for json_line in json_batch
                }

                for future in as_completed(future_to_log):
                    success, log_preview = future.result()
                    if success:
                        results['sent_logs'] += 1
                    else:
                        results['failed_logs'] += 1
                        results['errors'].append(f"Failed to send: {log_preview}")

            # Progress indicator
            processed = min(i + BATCH_SIZE, len(lines))
            print(f"📄 {file_path.name}: {processed}/{len(lines)} logs processed")

    except Exception as e:
        results['errors'].append(f"File processing error: {e}")

    return results

def main():
    print("🚀 Bulk Log Ingestion to Data-Ingestion Service")
    print("=" * 60)

    # Health check
    if not check_service_health():
        print("❌ Data-ingestion service is not healthy. Aborting.")
        return

    print("✅ Data-ingestion service is healthy")

    # Get all log files
    log_files = sorted(Path(LOG_FILES_DIR).glob("*.log"))
    if not log_files:
        print(f"❌ No log files found in {LOG_FILES_DIR}")
        return

    print(f"📁 Found {len(log_files)} log files to process")

    # Process all files
    total_results = {
        'files_processed': 0,
        'total_logs': 0,
        'valid_logs': 0,
        'sent_logs': 0,
        'failed_logs': 0,
        'errors': []
    }

    start_time = time.time()

    for log_file in log_files:
        print(f"\n🔄 Processing {log_file.name}...")
        file_results = process_log_file(log_file)

        total_results['files_processed'] += 1
        total_results['total_logs'] += file_results['total_logs']
        total_results['valid_logs'] += file_results['valid_logs']
        total_results['sent_logs'] += file_results['sent_logs']
        total_results['failed_logs'] += file_results['failed_logs']
        total_results['errors'].extend(file_results['errors'])

        print(f"   ✅ {file_results['sent_logs']}/{file_results['valid_logs']} logs sent successfully")

    # Final results
    end_time = time.time()
    duration = end_time - start_time

    print("\n" + "=" * 60)
    print("📊 BULK INGESTION RESULTS")
    print("=" * 60)
    print(f"📁 Files processed: {total_results['files_processed']}")
    print(f"📄 Total log lines: {total_results['total_logs']}")
    print(f"✅ Valid syslog entries: {total_results['valid_logs']}")
    print(f"📤 Successfully sent: {total_results['sent_logs']}")
    print(f"❌ Failed to send: {total_results['failed_logs']}")
    print(".2f")
    print(".2f")

    if total_results['errors']:
        print(f"\n⚠️  Errors encountered: {len(total_results['errors'])}")
        for error in total_results['errors'][:5]:  # Show first 5 errors
            print(f"   • {error}")
        if len(total_results['errors']) > 5:
            print(f"   ... and {len(total_results['errors']) - 5} more errors")

    success_rate = (total_results['sent_logs'] / total_results['valid_logs'] * 100) if total_results['valid_logs'] > 0 else 0
    if success_rate >= 95:
        print("\n🎯 Bulk ingestion completed successfully!")
    else:
        print(f"\n⚠️  Bulk ingestion completed with {100 - success_rate:.1f}% failure rate")

if __name__ == "__main__":
    main()