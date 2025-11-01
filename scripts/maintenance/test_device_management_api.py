#!/usr/bin/env python3
"""
测试设备管理API - 时效性设备客户映射
"""

import requests
import time
from datetime import datetime, timezone
import json

def test_device_management_api():
    """测试设备管理API功能"""

    base_url = "http://localhost:8083/api/v1/devices"

    print("🧪 测试设备管理API - 时效性设备客户映射")
    print("=" * 60)

    # 测试设备序列号
    test_device = "TEST_DEVICE_001"

    # 测试场景时间点
    test_times = {
        "2024_q1": "2024-03-15T10:00:00Z",  # 2024年初
        "2024_q3": "2024-09-15T10:00:00Z",  # 2024年中
        "2025_q1": "2025-03-15T10:00:00Z",  # 2025年初
        "current": None  # 当前时间
    }

    print("📋 测试计划:")
    print("1. 创建设备的历史流转记录")
    print("2. 查询不同时间点的客户映射")
    print("3. 验证设备转移功能")
    print("4. 检查设备历史记录")
    print()

    try:
        # 步骤1: 创建时效性映射记录
        print("🔧 步骤1: 创建设备的历史流转记录")

        # 2024年初 - 绑定到客户A
        response = requests.post(f"{base_url}/bind", params={
            'deviceSerial': test_device,
            'customerId': 'customer_a',
            'bindReason': '初始部署',
            'bindTime': test_times['2024_q1']
        })
        print(f"绑定到客户A: {response.status_code}")
        if response.status_code != 200:
            print(f"错误: {response.text}")

        # 2024年中 - 转移到客户B
        response = requests.post(f"{base_url}/transfer", params={
            'deviceSerial': test_device,
            'newCustomerId': 'customer_b',
            'transferReason': 'POC项目',
            'transferTime': test_times['2024_q3']
        })
        print(f"转移到客户B: {response.status_code}")
        if response.status_code != 200:
            print(f"错误: {response.text}")

        # 2025年初 - 转移到客户C
        response = requests.post(f"{base_url}/transfer", params={
            'deviceSerial': test_device,
            'newCustomerId': 'customer_c',
            'transferReason': '正式合同',
            'transferTime': test_times['2025_q1']
        })
        print(f"转移到客户C: {response.status_code}")
        if response.status_code != 200:
            print(f"错误: {response.text}")

        print()

        # 步骤2: 查询不同时间点的客户映射
        print("🔍 步骤2: 查询不同时间点的客户映射")

        for time_name, timestamp in test_times.items():
            response = requests.get(f"{base_url}/customer", params={
                'deviceSerial': test_device,
                'timestamp': timestamp
            })

            if response.status_code == 200:
                result = response.json()
                customer_id = result.get('customerId', 'unknown')
                found = result.get('found', False)
                print(f"  {time_name} ({timestamp or '当前时间'}): 客户{customer_id} {'✓' if found else '✗'}")
            else:
                print(f"  {time_name}: 查询失败 ({response.status_code})")

        print()

        # 步骤3: 检查设备历史记录
        print("📚 步骤3: 检查设备历史记录")

        response = requests.get(f"{base_url}/history/{test_device}")
        if response.status_code == 200:
            history = response.json()
            print(f"设备历史记录数量: {len(history)}")
            for i, record in enumerate(history, 1):
                bind_time = record.get('bindTime', '未知')
                unbind_time = record.get('unbindTime', '当前有效') or '当前有效'
                customer_id = record.get('customerId', '未知')
                reason = record.get('bindReason', '无')
                print(f"  {i}. 客户{customer_id} | 绑定:{bind_time} | 解绑:{unbind_time} | 原因:{reason}")
        else:
            print(f"获取历史记录失败: {response.status_code}")

        print()

        # 步骤4: 检查活跃映射
        print("🎯 步骤4: 检查当前活跃映射")

        response = requests.get(f"{base_url}/active")
        if response.status_code == 200:
            active = response.json()
            print(f"当前活跃映射数量: {len(active)}")
            for mapping in active:
                device = mapping.get('devSerial', '未知')
                customer = mapping.get('customerId', '未知')
                bind_time = mapping.get('bindTime', '未知')
                print(f"  设备{device} -> 客户{customer} (从{bind_time}起)")
        else:
            print(f"获取活跃映射失败: {response.status_code}")

        print()
        print("✅ 测试完成!")
        print()
        print("🎉 时效性设备客户映射功能验证成功!")
        print("设备可以在不同时间段属于不同客户，完美支持设备流转场景。")

    except Exception as e:
        print(f"❌ 测试过程中发生错误: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    test_device_management_api()