#!/usr/bin/env python3
"""
Concurrency Test Script
Tests concurrent request performance to identify connection reset issues
"""

import requests
import time
import concurrent.futures

# Configuration
API_BASE_URL = "http://localhost:8080"
TEST_LOG = '<14>Jan 15 02:10:15 hostname program: syslog_version=1.0, dev_serial=A458749D86A13BDE, log_type=1, sub_type=1, attack_mac=54:F6:E2:B3:AE:75, attack_ip=10.32.30.100, response_ip=10.32.30.50, response_port=22, line_id=1, Iface_type=1, Vlan_id=1, log_time=1642206615, eth_type=2054, ip_type=0'

def send_request(request_id):
    """Send a single request and return results"""
    try:
        start_time = time.time()
        response = requests.post(
            f"{API_BASE_URL}/api/v1/logs/ingest",
            data=TEST_LOG,
            headers={'Content-Type': 'text/plain'},
            timeout=30
        )
        end_time = time.time()

        return {
            'request_id': request_id,
            'status': response.status_code,
            'response_time': end_time - start_time,
            'success': response.status_code == 200,
            'error': None
        }
    except Exception as e:
        return {
            'request_id': request_id,
            'status': 'ERROR',
            'response_time': 0,
            'success': False,
            'error': str(e)
        }

def test_concurrency(concurrent_requests):
    """Test performance with specific concurrency level"""
    print(f"\n🧪 Testing {concurrent_requests} concurrent requests...")

    start_time = time.time()

    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrent_requests) as executor:
        futures = [executor.submit(send_request, i) for i in range(concurrent_requests)]
        results = [future.result() for future in concurrent.futures.as_completed(futures)]

    end_time = time.time()
    total_time = end_time - start_time

    # Analyze results
    success_count = sum(1 for r in results if r['success'])
    error_count = len(results) - success_count
    avg_response_time = sum(r['response_time'] for r in results if r['success']) / success_count if success_count > 0 else 0
    throughput = len(results) / total_time

    print(f"   ⏱️  Total time: {total_time:.3f}s")
    print(f"   ✅ Success: {success_count}/{len(results)}")
    print(f"   ❌ Errors: {error_count}")
    print(f"   📊 Throughput: {throughput:.1f} req/s")
    print(f"   🕐 Avg response: {avg_response_time:.3f}s")

    if error_count > 0:
        print("   🚨 Error details:")
        connection_resets = 0
        for r in results:
            if not r['success']:
                error_msg = r.get('error', 'Unknown error')
                if 'Connection reset by peer' in error_msg:
                    connection_resets += 1
                print(f"      Req {r['request_id']}: {error_msg}")
        print(f"   🔄 Connection resets: {connection_resets}")

    return {
        'concurrent_requests': concurrent_requests,
        'total_requests': len(results),
        'success_count': success_count,
        'error_count': error_count,
        'throughput': throughput,
        'avg_response_time': avg_response_time,
        'total_time': total_time
    }

def main():
    print("🚀 Concurrency Performance Test")
    print("=" * 50)

    # Check service health
    try:
        response = requests.get(f"{API_BASE_URL}/actuator/health", timeout=10)
        if response.status_code != 200:
            print("❌ Service is not healthy. Aborting test.")
            return
        print("✅ Service is healthy")
    except Exception as e:
        print(f"❌ Cannot connect to service: {e}")
        return

    # Test different concurrency levels
    concurrency_levels = [1, 2, 4, 8, 16, 32, 50]
    results = []

    for concurrent in concurrency_levels:
        result = test_concurrency(concurrent)
        results.append(result)

    # Summary analysis
    print("\n" + "=" * 50)
    print("📈 PERFORMANCE ANALYSIS")
    print("=" * 50)

    print("<8")
    print("-" * 70)
    for r in results:
        connection_reset_rate = "N/A"
        if r['error_count'] > 0:
            # Calculate connection reset rate if we have error details
            connection_reset_rate = f"{r['error_count']/r['total_requests']*100:.1f}%"
        print("<8")

    # Identify bottleneck
    max_throughput = max(r['throughput'] for r in results)
    max_throughput_concurrent = next(r['concurrent_requests'] for r in results if r['throughput'] == max_throughput)

    print(f"\n🎯 Key Findings:")
    print(f"   • Maximum throughput: {max_throughput:.1f} req/s (at {max_throughput_concurrent} concurrent requests)")
    print(f"   • Single request latency: {results[0]['avg_response_time']:.3f}s")

    # Check if connection resets correlate with high concurrency
    high_concurrency_results = [r for r in results if r['concurrent_requests'] >= 16]
    connection_reset_threshold = next((r for r in results if r['error_count'] > 0), None)

    if connection_reset_threshold:
        print(f"   • Connection resets start appearing at {connection_reset_threshold['concurrent_requests']} concurrent requests")
        print("   • This suggests a server-side concurrency limit or resource bottleneck")
    else:
        print("   • No connection resets detected - issue may be elsewhere")

if __name__ == "__main__":
    main()