#!/usr/bin/env python3
"""
插入测试告警数据的脚本
用于测试告警拉取功能
"""

import psycopg2
import json
from datetime import datetime, timedelta
import random


def random_mac():
    """Generate a random unicast MAC address (LSB of first octet = 0)."""
    first_octet = random.randint(0x00, 0xFE) & 0xFE  # clear multicast bit
    # Avoid null MAC (00:00:00:00:00:00) and broadcast (FF:FF:FF:FF:FF:FF)
    remaining = [random.randint(0x00, 0xFF) for _ in range(5)]
    if first_octet == 0 and all(b == 0 for b in remaining):
        remaining[-1] = random.randint(1, 0xFF)
    return ":".join(f"{b:02x}" for b in [first_octet] + remaining)


def insert_test_alerts():
    """插入一些测试告警数据"""

    # 数据库连接参数
    db_config = {
        "host": "localhost",
        "port": 5432,
        "database": "threat_detection",
        "user": "threat_user",
        "password": "threat_password",
    }

    try:
        # 连接数据库
        conn = psycopg2.connect(**db_config)
        cursor = conn.cursor()

        print("🧪 插入测试告警数据")
        print("=" * 40)

        # 测试告警数据
        test_alerts = [
            {
                "title": "高危威胁检测 - 端口扫描",
                "description": "检测到来自内网设备的端口扫描行为，威胁分数: 85.5",
                "severity": "HIGH",
                "attack_mac": random_mac(),
                "threat_score": 85.5,
                "metadata": {
                    "customer_id": "customer_a",
                    "attack_ip": "192.168.1.100",
                    "response_ip": "10.0.0.1",
                    "response_port": 3389,
                    "unique_ips": 3,
                    "unique_ports": 5,
                    "attack_count": 120,
                },
            },
            {
                "title": "严重威胁检测 - 横向移动",
                "description": "检测到APT横向移动行为，威胁分数: 145.2",
                "severity": "CRITICAL",
                "attack_mac": random_mac(),
                "threat_score": 145.2,
                "metadata": {
                    "customer_id": "customer_b",
                    "attack_ip": "192.168.2.50",
                    "response_ip": "10.0.0.2",
                    "response_port": 445,
                    "unique_ips": 8,
                    "unique_ports": 12,
                    "attack_count": 300,
                },
            },
            {
                "title": "中危威胁检测 - 异常连接",
                "description": "检测到异常网络连接行为，威胁分数: 65.8",
                "severity": "MEDIUM",
                "attack_mac": random_mac(),
                "threat_score": 65.8,
                "metadata": {
                    "customer_id": "customer_c",
                    "attack_ip": "192.168.3.25",
                    "response_ip": "10.0.0.3",
                    "response_port": 3306,
                    "unique_ips": 2,
                    "unique_ports": 3,
                    "attack_count": 45,
                },
            },
        ]

        # 插入告警
        for i, alert_data in enumerate(test_alerts, 1):
            # 随机生成创建时间（过去24小时内）
            hours_ago = random.randint(1, 24)
            created_at = datetime.now() - timedelta(hours=hours_ago)

            insert_query = """
            INSERT INTO alerts (
                title, description, status, severity, source,
                attack_mac, threat_score, metadata, created_at, updated_at
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """

            cursor.execute(
                insert_query,
                (
                    alert_data["title"],
                    alert_data["description"],
                    "NEW",
                    alert_data["severity"],
                    "threat-detection-system",
                    alert_data["attack_mac"],
                    alert_data["threat_score"],
                    json.dumps(alert_data["metadata"]),
                    created_at,
                    datetime.now(),
                ),
            )

            print(f"✅ 插入测试告警 #{i}: {alert_data['title']}")

        # 提交事务
        conn.commit()
        print(f"\n🎉 成功插入 {len(test_alerts)} 条测试告警数据")

    except Exception as e:
        print(f"❌ 数据库操作失败: {e}")
        if "conn" in locals():
            conn.rollback()
        raise
    finally:
        if "cursor" in locals():
            cursor.close()
        if "conn" in locals():
            conn.close()


if __name__ == "__main__":
    insert_test_alerts()
