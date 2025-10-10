#!/usr/bin/env python3
"""
Performance Monitoring Script for Data-Ingestion Service
Tests API response times and identifies performance bottlenecks
"""

import os
import json
import requests
import time
import statistics
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed
import psycopg2
from psycopg2 import pool

# Configuration
API_BASE_URL = "http://localhost:8080"
DB_CONFIG = {
    'host': 'localhost',
    'port': 5432,
    'database': 'threat_detection',
    'user': 'postgres',
    'password': 'postgres'
}
LOG_FILES_DIR = "/home/kylecui/threat-detection-system/tmp/real_test_logs"
TEST_BATCH_SIZE = 10  # Smaller batch for testing
CONCURRENT_REQUESTS = [1, 2, 4, 8, 16]  # Test different concurrency levels

class PerformanceMonitor:
    def __init__(self):
        self.db_pool = None
        self.test_logs = []

    def setup_db_connection(self):
        """Setup database connection pool"""
        try:
            self.db_pool = psycopg2.pool.SimpleConnectionPool(
                1, 20,  # min, max connections
                **DB_CONFIG
            )
            print("✅ Database connection pool established")
        except Exception as e:
            print(f"❌ Database connection failed: {e}")
            return False
        return True

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

    def extract_dev_serial(self, syslog_content):
        """Extract dev_serial from syslog content"""
        try:
            import re
            match = re.search(r'dev_serial=([0-9A-Za-z]+)', syslog_content)
            return match.group(1) if match else None
        except:
            return None

    def test_api_response_time(self, concurrency, num_requests=50):
        """Test API response time at different concurrency levels"""
        print(f"\n🔬 Testing API response time with {concurrency} concurrent requests...")

        response_times = []
        success_count = 0
        error_count = 0

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
                    return response_time, True, None
                else:
                    return response_time, False, f"HTTP {response.status_code}"

            except requests.exceptions.Timeout:
                end_time = time.time()
                response_time = (end_time - start_time) * 1000
                return response_time, False, "Timeout"
            except Exception as e:
                end_time = time.time()
                response_time = (end_time - start_time) * 1000
                return response_time, False, str(e)

        # Run concurrent requests
        with ThreadPoolExecutor(max_workers=concurrency) as executor:
            futures = []
            for i in range(min(num_requests, len(self.test_logs))):
                future = executor.submit(single_request, self.test_logs[i])
                futures.append(future)

            for future in as_completed(futures):
                response_time, success, error = future.result()
                response_times.append(response_time)
                if success:
                    success_count += 1
                else:
                    error_count += 1

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

            return {
                'concurrency': concurrency,
                'avg_time': avg_time,
                'median_time': median_time,
                'min_time': min_time,
                'max_time': max_time,
                'p95_time': p95_time,
                'success_count': success_count,
                'error_count': error_count,
                'success_rate': success_count / num_requests * 100
            }

        return None

    def test_database_query_performance(self, num_queries=100):
        """Test database query performance for dev_serial mapping"""
        print(f"\n🗄️  Testing database query performance ({num_queries} queries)...")

        if not self.db_pool:
            print("❌ Database connection not available")
            return None

        query_times = []
        cache_hit_count = 0
        cache_miss_count = 0

        # Extract dev_serials from test logs
        dev_serials = []
        for log in self.test_logs[:num_queries]:
            dev_serial = self.extract_dev_serial(log)
            if dev_serial:
                dev_serials.append(dev_serial.upper())

        if not dev_serials:
            print("❌ No dev_serials found in test logs")
            return None

        def single_query(dev_serial):
            conn = None
            try:
                conn = self.db_pool.getconn()
                cursor = conn.cursor()

                start_time = time.time()
                cursor.execute("""
                    SELECT customer_id FROM device_customer_mapping
                    WHERE dev_serial = %s AND is_active = true
                """, (dev_serial,))
                result = cursor.fetchone()
                end_time = time.time()

                query_time = (end_time - start_time) * 1000  # Convert to ms

                if result:
                    cache_hit_count += 1
                else:
                    cache_miss_count += 1

                return query_time, result is not None

            except Exception as e:
                print(f"❌ Database query error: {e}")
                return None, False
            finally:
                if conn:
                    self.db_pool.putconn(conn)

        # Run queries sequentially (database queries are typically fast)
        for dev_serial in dev_serials:
            result = single_query(dev_serial)
            if result[0] is not None:
                query_times.append(result[0])

        if query_times:
            avg_time = statistics.mean(query_times)
            median_time = statistics.median(query_times)
            min_time = min(query_times)
            max_time = max(query_times)

            print(f"   Average query time: {avg_time:.3f}ms")
            print(f"   Median query time: {median_time:.3f}ms")
            print(f"   Min query time: {min_time:.3f}ms")
            print(f"   Max query time: {max_time:.3f}ms")
            print(f"   Cache hits: {cache_hit_count}, Cache misses: {cache_miss_count}")

            return {
                'avg_time': avg_time,
                'median_time': median_time,
                'min_time': min_time,
                'max_time': max_time,
                'cache_hits': cache_hit_count,
                'cache_misses': cache_miss_count
            }

        return None

    def run_comprehensive_test(self):
        """Run comprehensive performance tests"""
        print("🚀 Starting Comprehensive Performance Analysis")
        print("=" * 60)

        # Setup
        if not self.setup_db_connection():
            return

        if not self.load_test_logs():
            return

        # Test database performance
        db_results = self.test_database_query_performance()

        # Test API performance at different concurrency levels
        api_results = []
        for concurrency in CONCURRENT_REQUESTS:
            result = self.test_api_response_time(concurrency)
            if result:
                api_results.append(result)

        # Analysis
        self.analyze_results(db_results, api_results)

    def analyze_results(self, db_results, api_results):
        """Analyze performance results and identify bottlenecks"""
        print("\n" + "=" * 60)
        print("📊 PERFORMANCE ANALYSIS & BOTTLENECK IDENTIFICATION")
        print("=" * 60)

        if db_results:
            print("🗄️  DATABASE PERFORMANCE:")
            print(f"   Average query time: {db_results['avg_time']:.3f}ms")
            print(f"   Median query time: {db_results['median_time']:.3f}ms")
            print(f"   Cache efficiency: {db_results['cache_hits']/(db_results['cache_hits']+db_results['cache_misses'])*100:.1f}%")

        print("\n🌐 API PERFORMANCE BY CONCURRENCY:")

        bottlenecks = []

        for result in api_results:
            print(f"   Concurrency {result['concurrency']}: "
                  ".1f"
                  f"({result['success_rate']:.1f}% success)")

            # Identify potential bottlenecks
            if result['avg_time'] > 1000:  # Over 1 second average
                bottlenecks.append(f"High latency at concurrency {result['concurrency']} ({result['avg_time']:.1f}ms)")
            if result['success_rate'] < 95:  # Below 95% success rate
                bottlenecks.append(f"Low success rate at concurrency {result['concurrency']} ({result['success_rate']:.1f}%)")
            if result['p95_time'] > 5000:  # 95th percentile over 5 seconds
                bottlenecks.append(f"High tail latency at concurrency {result['concurrency']} (P95: {result['p95_time']:.1f}ms)")

        print("\n🔍 BOTTLENECK ANALYSIS:")

        if not bottlenecks:
            print("   ✅ No significant performance bottlenecks detected")
            print("   📝 Recommendation: Current configuration handles the load well")
        else:
            print("   ⚠️  Identified potential bottlenecks:")
            for bottleneck in bottlenecks:
                print(f"   • {bottleneck}")

        # Specific recommendations
        print("\n💡 RECOMMENDATIONS:")

        if db_results and db_results['avg_time'] > 10:
            print("   • Database queries are slow - consider adding database indexes")
            print("   • Check database connection pooling configuration")

        slow_api_found = any(r['avg_time'] > 500 for r in api_results)
        if slow_api_found:
            print("   • API response times are high - check service thread pool configuration")
            print("   • Consider implementing request queuing or rate limiting")
            print("   • Review Kafka producer configuration for potential blocking")

        low_success_found = any(r['success_rate'] < 95 for r in api_results)
        if low_success_found:
            print("   • Success rates are inconsistent - implement retry logic with exponential backoff")
            print("   • Add circuit breaker pattern to handle service overload")
            print("   • Increase timeout values for high-concurrency scenarios")

        # Check for concurrency scaling issues
        if len(api_results) > 1:
            times = [r['avg_time'] for r in api_results]
            if times[-1] > times[0] * 2:  # Response time doubled with higher concurrency
                print("   • Service doesn't scale well with concurrency - review thread pool settings")
                print("   • Consider asynchronous processing or message queuing")

def main():
    monitor = PerformanceMonitor()
    monitor.run_comprehensive_test()

if __name__ == "__main__":
    main()