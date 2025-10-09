#!/usr/bin/env python3
"""
Simplified Performance Monitoring Script for Data-Ingestion Service
Tests API response times to identify performance bottlenecks
"""

import os
import json
import requests
import time
import statistics
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed

# Configuration
API_BASE_URL = "http://localhost:8080"
LOG_FILES_DIR = "/home/kylecui/threat-detection-system/tmp/real_test_logs"
TEST_BATCH_SIZE = 10
CONCURRENT_REQUESTS = [1, 2, 4, 8, 16]

class PerformanceMonitor:
    def __init__(self):
        self.test_logs = []

    def load_test_logs(self, limit=100):
        """Load sample logs for testing"""
        log_files = sorted(Path(LOG_FILES_DIR).glob("*.log"))
        if not log_files:
            print(f"❌ No log files found in {LOG_FILES_DIR}")
            return False

        print(f"📁 Loading test logs from {len(log_files)} files...")

        for log_file in log_files[:3]:  # Test with first 3 files
            try:
                with open(log_file, 'r', encoding='utf-8') as f:
                    lines = f.readlines()

                for line in lines[:limit//3]:  # Distribute limit across files
                    try:
                        log_data = json.loads(line.strip())
                        syslog_content = None

                        # Extract syslog content
                        if 'event' in log_data and 'original' in log_data['event']:
                            syslog_content = log_data['event']['original']
                        elif 'message' in log_data:
                            syslog_content = log_data['message']

                        if syslog_content and 'dev_serial=' in syslog_content:
                            self.test_logs.append(syslog_content)

                    except json.JSONDecodeError:
                        continue

                if len(self.test_logs) >= limit:
                    break

            except Exception as e:
                print(f"⚠️  Error reading {log_file.name}: {e}")

        print(f"✅ Loaded {len(self.test_logs)} test logs")
        return len(self.test_logs) > 0

    def test_api_response_time(self, concurrency, num_requests=50):
        """Test API response time at different concurrency levels"""
        print(f"\n🔬 Testing API response time with {concurrency} concurrent requests...")

        response_times = []
        success_count = 0
        error_count = 0
        timeout_count = 0

        def single_request(log_content):
            start_time = time.time()
            try:
                response = requests.post(
                    f"{API_BASE_URL}/api/v1/logs/ingest",
                    data=log_content,
                    headers={'Content-Type': 'text/plain'},
                    timeout=30
                )
                end_time = time.time()
                response_time = (end_time - start_time) * 1000  # Convert to ms

                if response.status_code == 200:
                    return response_time, True, None, "success"
                else:
                    return response_time, False, f"HTTP {response.status_code}", "http_error"

            except requests.exceptions.Timeout:
                end_time = time.time()
                response_time = (end_time - start_time) * 1000
                return response_time, False, "Timeout", "timeout"
            except Exception as e:
                end_time = time.time()
                response_time = (end_time - start_time) * 1000
                return response_time, False, str(e), "exception"

        # Run concurrent requests
        with ThreadPoolExecutor(max_workers=concurrency) as executor:
            futures = []
            for i in range(min(num_requests, len(self.test_logs))):
                future = executor.submit(single_request, self.test_logs[i])
                futures.append(future)

            for future in as_completed(futures):
                response_time, success, error, error_type = future.result()
                response_times.append(response_time)
                if success:
                    success_count += 1
                else:
                    error_count += 1
                    if error_type == "timeout":
                        timeout_count += 1

        # Calculate statistics
        if response_times:
            avg_time = statistics.mean(response_times)
            median_time = statistics.median(response_times)
            min_time = min(response_times)
            max_time = max(response_times)
            p95_time = statistics.quantiles(response_times, n=20)[18]  # 95th percentile

            print(f"   Average response time: {avg_time:.1f}ms")
            print(f"   Median response time: {median_time:.1f}ms")
            print(f"   Min response time: {min_time:.1f}ms")
            print(f"   Max response time: {max_time:.1f}ms")
            print(f"   95th percentile: {p95_time:.1f}ms")
            print(f"   Success rate: {success_count}/{num_requests} ({success_count/num_requests*100:.1f}%)")
            if timeout_count > 0:
                print(f"   Timeouts: {timeout_count}")

            return {
                'concurrency': concurrency,
                'avg_time': avg_time,
                'median_time': median_time,
                'min_time': min_time,
                'max_time': max_time,
                'p95_time': p95_time,
                'success_count': success_count,
                'error_count': error_count,
                'timeout_count': timeout_count,
                'success_rate': success_count / num_requests * 100
            }

        return None

    def run_comprehensive_test(self):
        """Run comprehensive performance tests"""
        print("🚀 Starting API Performance Analysis")
        print("=" * 60)

        if not self.load_test_logs():
            return

        # Test API performance at different concurrency levels
        api_results = []
        for concurrency in CONCURRENT_REQUESTS:
            result = self.test_api_response_time(concurrency)
            if result:
                api_results.append(result)

        # Analysis
        self.analyze_results(api_results)

    def analyze_results(self, api_results):
        """Analyze performance results and identify bottlenecks"""
        print("\n" + "=" * 60)
        print("📊 PERFORMANCE ANALYSIS & BOTTLENECK IDENTIFICATION")
        print("=" * 60)

        print("🌐 API PERFORMANCE BY CONCURRENCY:")

        bottlenecks = []
        performance_degradation = []

        for result in api_results:
            print(f"   Concurrency {result['concurrency']}: "
                  f"{result['avg_time']:.1f}ms avg "
                  f"({result['success_rate']:.1f}% success)")

            # Identify potential bottlenecks
            if result['avg_time'] > 1000:  # Over 1 second average
                bottlenecks.append(f"High latency at concurrency {result['concurrency']} ({result['avg_time']:.1f}ms)")
            if result['success_rate'] < 95:  # Below 95% success rate
                bottlenecks.append(f"Low success rate at concurrency {result['concurrency']} ({result['success_rate']:.1f}%)")
            if result['timeout_count'] > 0:
                bottlenecks.append(f"Timeouts occurring at concurrency {result['concurrency']} ({result['timeout_count']} timeouts)")
            if result['p95_time'] > 5000:  # 95th percentile over 5 seconds
                bottlenecks.append(f"High tail latency at concurrency {result['concurrency']} (P95: {result['p95_time']:.1f}ms)")

        # Check for concurrency scaling issues
        if len(api_results) > 1:
            base_time = api_results[0]['avg_time']  # Concurrency 1
            for result in api_results[1:]:
                degradation_ratio = result['avg_time'] / base_time
                if degradation_ratio > 2:  # More than doubled
                    performance_degradation.append(f"Performance degraded {degradation_ratio:.1f}x at concurrency {result['concurrency']}")

        print("\n🔍 BOTTLENECK ANALYSIS:")

        if not bottlenecks and not performance_degradation:
            print("   ✅ No significant performance bottlenecks detected")
            print("   📝 Recommendation: Current configuration handles the load well")
        else:
            if bottlenecks:
                print("   ⚠️  Identified potential bottlenecks:")
                for bottleneck in bottlenecks:
                    print(f"   • {bottleneck}")

            if performance_degradation:
                print("   ⚠️  Performance scaling issues:")
                for issue in performance_degradation:
                    print(f"   • {issue}")

        # Specific recommendations
        print("\n💡 RECOMMENDATIONS:")

        slow_responses = any(r['avg_time'] > 500 for r in api_results)
        if slow_responses:
            print("   • API response times are high - potential causes:")
            print("     - Service thread pool exhausted under load")
            print("     - Database connection pool contention")
            print("     - Kafka producer blocking on send operations")
            print("     - JVM garbage collection pauses")

        low_success = any(r['success_rate'] < 95 for r in api_results)
        if low_success:
            print("   • Success rates are inconsistent - implement:")
            print("     - Retry logic with exponential backoff")
            print("     - Circuit breaker pattern for fault tolerance")
            print("     - Request queuing during peak loads")

        timeouts = any(r['timeout_count'] > 0 for r in api_results)
        if timeouts:
            print("   • Timeouts occurring - increase timeout values or:")
            print("     - Optimize database queries with proper indexing")
            print("     - Implement async processing for long-running operations")
            print("     - Add connection pooling and keep-alive")

        scaling_issues = len(performance_degradation) > 0
        if scaling_issues:
            print("   • Service doesn't scale well with concurrency:")
            print("     - Increase thread pool size in application.yml")
            print("     - Consider horizontal scaling (multiple service instances)")
            print("     - Implement request throttling/rate limiting")

        print("\n🔧 IMMEDIATE ACTIONS TO TRY:")
        print("   1. Check application thread pool configuration")
        print("   2. Monitor database connection pool usage")
        print("   3. Review Kafka producer settings for async operation")
        print("   4. Add JVM performance monitoring (GC, heap usage)")

def main():
    monitor = PerformanceMonitor()
    monitor.run_comprehensive_test()

if __name__ == "__main__":
    main()