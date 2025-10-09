#!/usr/bin/env python3
import json
import requests
import sys
import time
from pathlib import Path

def process_log_file(log_file_path, api_url, batch_size=100):
    """Process log file and send to ingestion API in batches"""

    print(f"Processing log file: {log_file_path}")
    print(f"API URL: {api_url}")
    print(f"Batch size: {batch_size}")

    processed_count = 0
    success_count = 0
    error_count = 0

    with open(log_file_path, 'r', encoding='utf-8') as f:
        batch = []

        for line_num, line in enumerate(f, 1):
            try:
                # Parse JSON line
                log_entry = json.loads(line.strip())

                # Extract the original syslog message
                if 'event' in log_entry and 'original' in log_entry['event']:
                    syslog_message = log_entry['event']['original']
                    batch.append(syslog_message)
                    processed_count += 1

                    # Send batch when it reaches the limit
                    if len(batch) >= batch_size:
                        if send_batch(batch, api_url):
                            success_count += len(batch)
                        else:
                            error_count += len(batch)
                        batch = []

                        print(f"Processed {processed_count} logs, Success: {success_count}, Errors: {error_count}")

                else:
                    print(f"Warning: Invalid log format at line {line_num}")
                    error_count += 1

            except json.JSONDecodeError as e:
                print(f"Error parsing JSON at line {line_num}: {e}")
                error_count += 1
            except Exception as e:
                print(f"Unexpected error at line {line_num}: {e}")
                error_count += 1

        # Send remaining batch
        if batch:
            if send_batch(batch, api_url):
                success_count += len(batch)
            else:
                error_count += len(batch)

    print("\nFinal Results:")
    print(f"Total processed: {processed_count}")
    print(f"Successful: {success_count}")
    print(f"Errors: {error_count}")

    return success_count, error_count

def send_batch(batch, api_url):
    """Send a batch of logs to the API"""
    payload = {"logs": batch}

    try:
        response = requests.post(api_url, json=payload, timeout=30)
        response.raise_for_status()

        result = response.json()
        print(f"Batch sent successfully. Success: {result.get('successCount', 0)}, Errors: {result.get('errorCount', 0)}")
        return True

    except requests.exceptions.RequestException as e:
        print(f"Error sending batch: {e}")
        return False
    except json.JSONDecodeError as e:
        print(f"Error parsing response: {e}")
        return False

def main():
    if len(sys.argv) != 2:
        print("Usage: python ingest_logs.py <log_file_path>")
        sys.exit(1)

    log_file_path = sys.argv[1]

    if not Path(log_file_path).exists():
        print(f"Error: Log file does not exist: {log_file_path}")
        sys.exit(1)

    api_url = "http://localhost:8080/api/v1/logs/batch"

    # Process the log file
    success_count, error_count = process_log_file(log_file_path, api_url, batch_size=200)

    if error_count > 0:
        print(f"\nWarning: {error_count} logs failed to process")
        sys.exit(1)
    else:
        print("\nAll logs processed successfully!")

if __name__ == "__main__":
    main()