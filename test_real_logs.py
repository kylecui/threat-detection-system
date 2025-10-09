#!/usr/bin/env python3
"""
Real Multi-Tenant Log Processing Test
Tests the complete threat detection system with real production logs
"""

import os
import json
import requests
import time
from pathlib import Path
from collections import defaultdict

# Configuration
API_BASE_URL = "http://localhost:8080"
LOG_FILES_DIR = "/home/kylecui/threat-detection-system/tmp/real_test_logs"
MAX_FILES_TO_PROCESS = 5  # Limit for testing

def test_health():
    """Test service health"""
    try:
        response = requests.get(f"{API_BASE_URL}/actuator/health", timeout=5)
        return response.status_code == 200 and response.json().get('status') == 'UP'
    except Exception as e:
        print(f"Health check failed: {e}")
        return False

def test_customer_mapping():
    """Test customer mapping for known devices"""
    test_cases = [
        ("GSFB2204200410007425", "customer_b"),
        ("caa0beea29676c6d", "customer_f"),
        ("UNKNOWN_DEVICE", "unknown")
    ]

    print("\n=== Customer Mapping Test ===")
    for dev_serial, expected_customer in test_cases:
        try:
            response = requests.get(f"{API_BASE_URL}/api/v1/logs/customer-mapping/{dev_serial}", timeout=5)
            if response.status_code == 200:
                result = response.text.strip()
                expected = f"DevSerial: {dev_serial} -> Customer: {expected_customer}"
                status = "✅ PASS" if result == expected else "❌ FAIL"
                print(f"{status} {dev_serial}: {result}")
            else:
                print(f"❌ FAIL {dev_serial}: HTTP {response.status_code}")
        except Exception as e:
            print(f"❌ ERROR {dev_serial}: {e}")

def process_log_file(file_path):
    """Process a single log file and return results"""
    results = {
        'total_logs': 0,
        'successful_parses': 0,
        'failed_parses': 0,
        'customers_found': set(),
        'device_serials': set()
    }

    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            for line_num, line in enumerate(f, 1):
                if results['total_logs'] >= 100:  # Limit per file for testing
                    break

                line = line.strip()
                if not line:
                    continue

                results['total_logs'] += 1

                try:
                    # Parse JSON log
                    log_data = json.loads(line)
                    syslog_content = None

                    # Extract syslog content from various possible fields
                    if 'event' in log_data and 'original' in log_data['event']:
                        syslog_content = log_data['event']['original']
                    elif 'message' in log_data:
                        syslog_content = log_data['message']
                    elif 'log' in log_data and 'syslog' in log_data['log'] and 'original' in log_data['log']['syslog']:
                        syslog_content = log_data['log']['syslog']['original']

                    if syslog_content and 'dev_serial=' in syslog_content:
                        # Extract device serial
                        dev_serial_start = syslog_content.find('dev_serial=') + 11
                        dev_serial_end = syslog_content.find(',', dev_serial_start)
                        if dev_serial_end == -1:
                            dev_serial_end = len(syslog_content)
                        dev_serial = syslog_content[dev_serial_start:dev_serial_end].strip()

                        if dev_serial:
                            results['device_serials'].add(dev_serial)

                            # Test API parsing
                            try:
                                response = requests.post(
                                    f"{API_BASE_URL}/api/v1/logs/ingest",
                                    data=syslog_content,
                                    headers={'Content-Type': 'text/plain'},
                                    timeout=10
                                )

                                if response.status_code == 200:
                                    results['successful_parses'] += 1

                                    # Get customer mapping
                                    mapping_response = requests.get(
                                        f"{API_BASE_URL}/api/v1/logs/customer-mapping/{dev_serial}",
                                        timeout=5
                                    )
                                    if mapping_response.status_code == 200:
                                        mapping_text = mapping_response.text
                                        if '-> Customer: ' in mapping_text:
                                            customer = mapping_text.split('-> Customer: ')[1]
                                            results['customers_found'].add(customer)

                                else:
                                    results['failed_parses'] += 1
                                    print(f"❌ Parse failed for {dev_serial}: HTTP {response.status_code}")

                            except Exception as e:
                                results['failed_parses'] += 1
                                print(f"❌ API error for {dev_serial}: {e}")

                except json.JSONDecodeError:
                    results['failed_parses'] += 1
                    print(f"❌ JSON parse error in {file_path}:{line_num}")

    except Exception as e:
        print(f"❌ Error processing {file_path}: {e}")

    return results

def run_comprehensive_test():
    """Run comprehensive multi-tenant testing"""
    print("🚀 Starting Real Multi-Tenant Log Processing Test")
    print("=" * 60)

    # Health check
    if not test_health():
        print("❌ Service health check failed. Aborting test.")
        return

    print("✅ Service is healthy")

    # Customer mapping test
    test_customer_mapping()

    # Process log files
    print("\n=== Processing Real Log Files ===")

    log_files = sorted(Path(LOG_FILES_DIR).glob("*.log"))[:MAX_FILES_TO_PROCESS]
    total_results = {
        'total_logs': 0,
        'successful_parses': 0,
        'failed_parses': 0,
        'customers_found': set(),
        'device_serials': set(),
        'files_processed': 0
    }

    for log_file in log_files:
        print(f"\n📄 Processing {log_file.name}...")
        file_results = process_log_file(log_file)
        total_results['files_processed'] += 1

        for key in ['total_logs', 'successful_parses', 'failed_parses']:
            total_results[key] += file_results[key]

        total_results['customers_found'].update(file_results['customers_found'])
        total_results['device_serials'].update(file_results['device_serials'])

        print(f"   📊 {file_results['total_logs']} logs, {file_results['successful_parses']} successful, {file_results['failed_parses']} failed")
        print(f"   👥 Customers: {sorted(file_results['customers_found'])}")
        print(f"   📱 Devices: {len(file_results['device_serials'])} unique")

    # Final summary
    print("\n" + "=" * 60)
    print("📈 FINAL TEST RESULTS")
    print("=" * 60)
    print(f"📁 Files processed: {total_results['files_processed']}")
    print(f"📊 Total logs processed: {total_results['total_logs']}")
    print(f"✅ Successful parses: {total_results['successful_parses']}")
    print(f"❌ Failed parses: {total_results['failed_parses']}")
    print(f"👥 Unique customers found: {sorted(total_results['customers_found'])}")
    print(f"📱 Unique device serials: {len(total_results['device_serials'])}")

    success_rate = (total_results['successful_parses'] / total_results['total_logs'] * 100) if total_results['total_logs'] > 0 else 0
    print(f"📈 Success rate: {success_rate:.1f}%")
    # Multi-tenancy verification
    expected_customers = {'customer_b', 'customer_f'}  # Based on the devices we saw
    found_customers = total_results['customers_found']

    if expected_customers.issubset(found_customers):
        print("✅ Multi-tenancy isolation: All expected customers found")
    else:
        missing = expected_customers - found_customers
        print(f"⚠️  Multi-tenancy check: Missing customers {missing}")

    print("\n🎯 Test completed successfully!" if success_rate >= 95 else "\n⚠️  Test completed with issues")

if __name__ == "__main__":
    run_comprehensive_test()