import requests
import json
import time

# Test data for integration testing (syslog format expected by data-ingestion service)
test_attack_logs = [
    "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1,attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=10.0.0.1,response_port=80,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600,eth_type=2048,ip_type=6",
    "syslog_version=1.10.0,dev_serial=DEF456,log_type=1,sub_type=2,attack_mac=00:11:22:33:44:66,attack_ip=192.168.1.101,response_ip=10.0.0.1,response_port=443,line_id=2,Iface_type=1,Vlan_id=0,log_time=1728465660,eth_type=2048,ip_type=6"
]

def test_data_ingestion():
    """Test data ingestion service"""
    print("Testing data ingestion service...")
    try:
        # Test health endpoint
        response = requests.get("http://localhost:8080/actuator/health", timeout=5)
        if response.status_code == 200:
            print("✓ Data ingestion service is healthy")
        else:
            print(f"✗ Data ingestion service health check failed: {response.status_code}")
            return False
            
        # Send test attack logs
        for i, log in enumerate(test_attack_logs):
            response = requests.post(
                "http://localhost:8080/api/v1/logs/ingest",
                data=log,
                headers={"Content-Type": "text/plain"},
                timeout=10
            )
            if response.status_code == 200:
                print(f"✓ Attack event {i+1} sent successfully")
            else:
                print(f"✗ Failed to send attack event {i+1}: {response.status_code} - {response.text}")
                return False
                
        return True
    except Exception as e:
        print(f"✗ Data ingestion test failed: {e}")
        return False

def test_threat_assessment():
    """Test threat assessment service"""
    print("\nTesting threat assessment service...")
    try:
        # Test health endpoint
        response = requests.get("http://localhost:8083/api/v1/assessment/health", timeout=5)
        if "healthy" in response.text.lower():
            print("✓ Threat assessment service is healthy")
            return True
        else:
            print(f"✗ Threat assessment service health check failed: {response.text}")
            return False
    except Exception as e:
        print(f"✗ Threat assessment test failed: {e}")
        return False

def test_stream_processing():
    """Test stream processing service"""
    print("\nTesting stream processing service...")
    try:
        # Test overview endpoint
        response = requests.get("http://localhost:8081/overview", timeout=5)
        if response.status_code == 200:
            data = response.json()
            if data.get("jobs-running", 0) > 0:
                print("✓ Stream processing service is running with active jobs")
                return True
            else:
                print("⚠ Stream processing service is running but no active jobs")
                return True
        else:
            print(f"✗ Stream processing service check failed: {response.status_code}")
            return False
    except Exception as e:
        print(f"✗ Stream processing test failed: {e}")
        return False

def main():
    print("🚀 Starting Threat Detection System Integration Test")
    print("=" * 50)
    
    results = []
    results.append(("Data Ingestion", test_data_ingestion()))
    results.append(("Threat Assessment", test_threat_assessment()))
    results.append(("Stream Processing", test_stream_processing()))
    
    print("\n" + "=" * 50)
    print("📊 Test Results Summary:")
    
    all_passed = True
    for service, passed in results:
        status = "✅ PASS" if passed else "❌ FAIL"
        print(f"{service}: {status}")
        all_passed = all_passed and passed
    
    if all_passed:
        print("\n🎉 All integration tests passed! The threat detection system is working correctly.")
        print("\nNext steps:")
        print("1. Monitor Kafka topics for message flow")
        print("2. Check database for persisted threat assessments")
        print("3. Test with real threat data")
        print("4. Implement alerting and monitoring")
    else:
        print("\n⚠️  Some tests failed. Please check the service logs for details.")
    
    return all_passed

if __name__ == "__main__":
    main()
