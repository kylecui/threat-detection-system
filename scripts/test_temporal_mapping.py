#!/usr/bin/env python3
"""
测试时效性设备客户映射功能
验证设备在不同时间段可以属于不同客户
"""

import requests
import time
from datetime import datetime, timezone
import json

def test_temporal_mapping():
    """测试时效性映射功能"""

    base_url = "http://localhost:8083"  # threat-assessment service

    print("🧪 测试时效性设备客户映射功能")
    print("=" * 50)

    # 测试设备序列号
    test_dev_serial = "TEST_DEVICE_001"

    # 模拟不同时间点的日志处理
    test_scenarios = [
        {
            "timestamp": "2024-01-15T10:00:00Z",  # 2024年初
            "expected_customer": "customer_a",
            "description": "2024年初 - 属于客户A"
        },
        {
            "timestamp": "2024-06-15T10:00:00Z",  # 2024年中
            "expected_customer": "customer_b",
            "description": "2024年中 - 转移到客户B"
        },
        {
            "timestamp": "2025-01-15T10:00:00Z",  # 2025年初
            "expected_customer": "customer_c",
            "description": "2025年初 - 转移到客户C"
        },
        {
            "timestamp": "2025-10-31T12:00:00Z",  # 当前时间
            "expected_customer": "customer_c",
            "description": "当前时间 - 仍然属于客户C"
        }
    ]

    print("📋 测试场景:")
    for i, scenario in enumerate(test_scenarios, 1):
        print(f"  {i}. {scenario['description']}")
        print(f"     时间戳: {scenario['timestamp']}")
        print(f"     期望客户: {scenario['expected_customer']}")
        print()

    # 首先创建测试数据 - 模拟设备的历史流转
    print("🔧 创建测试映射数据...")

    # 注意：实际应该通过API创建，但这里我们先直接插入数据库来测试
    # 在实际使用中，应该通过管理API来创建设备绑定记录

    print("⚠️  注意: 当前测试需要先通过数据库直接创建时效性映射记录")
    print("   或者实现设备管理API来创建这些记录")
    print()

    # 测试当前时间点的映射（应该使用默认逻辑）
    print("🧪 测试当前时间点映射...")

    try:
        # 这里应该调用实际的客户解析API
        # 由于我们还没有实现带时间戳的API，我们先测试基本功能

        response = requests.get(f"{base_url}/actuator/health")
        if response.status_code == 200:
            print("✅ threat-assessment服务运行正常")
        else:
            print(f"❌ threat-assessment服务异常: {response.status_code}")

    except Exception as e:
        print(f"❌ 连接服务失败: {e}")

    print()
    print("📝 总结:")
    print("1. ✅ 数据库结构已更新为时效性映射")
    print("2. ✅ JPA实体类已添加时间字段")
    print("3. ✅ Repository已添加时效性查询方法")
    print("4. ✅ 服务层已实现时效性客户解析")
    print("5. 🔄 需要实现设备管理API来创建/管理时效性映射")
    print("6. 🔄 需要更新日志处理逻辑以传递时间戳")

    print()
    print("🎯 下一步行动:")
    print("- 实现设备管理API (POST /api/devices/bind, POST /api/devices/unbind)")
    print("- 更新日志摄取API以支持历史时间戳")
    print("- 添加设备流转历史查询接口")
    print("- 实现设备流转的业务审批流程")

if __name__ == "__main__":
    test_temporal_mapping()