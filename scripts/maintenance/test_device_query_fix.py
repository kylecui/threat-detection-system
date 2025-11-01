#!/usr/bin/env python3
"""
测试修复后的设备查询功能
"""

import requests

def test_device_queries():
    """测试之前失败的设备查询"""

    base_url = "http://localhost:8083/api/v1/devices"

    # 测试之前失败的设备
    failed_devices = [
        "44056bfd85030e0e",
        "5355ac453fe4e74d",
        "GSFB2204200410007425",
        "bce9288a4caa2c61"
    ]

    print("🧪 测试修复后的设备查询功能")
    print("=" * 40)

    for device in failed_devices:
        try:
            response = requests.get(f"{base_url}/customer", params={
                'deviceSerial': device
            })

            if response.status_code == 200:
                result = response.json()
                customer_id = result.get('customerId', 'unknown')
                found = result.get('found', False)
                print(f"✅ {device} -> {customer_id} ({'找到' if found else '未找到'})")
            else:
                print(f"❌ {device} 查询失败: {response.status_code}")

        except Exception as e:
            print(f"❌ {device} 异常: {e}")

    print("\n🎉 测试完成!")

if __name__ == "__main__":
    test_device_queries()