#!/usr/bin/env python3
"""
拉取最新告警信息的脚本 (修复版)
用于检查和测试告警系统
"""

import psycopg2
import argparse
from datetime import datetime, timedelta
import json

def fetch_recent_alerts(hours=24, limit=50):
    """拉取最近的告警信息"""

    # 数据库连接参数
    db_config = {
        'host': 'localhost',
        'port': 5432,
        'database': 'threat_detection',
        'user': 'threat_user',
        'password': 'threat_password'
    }

    try:
        # 连接数据库
        conn = psycopg2.connect(**db_config)
        cursor = conn.cursor()

        print("🔍 拉取最新告警信息")
        print("=" * 60)

        # 计算时间范围
        since_time = datetime.now() - timedelta(hours=hours)

        # 查询最近的告警
        query = """
        SELECT
            id,
            title,
            description,
            status,
            severity,
            source,
            attack_mac,
            threat_score,
            created_at,
            updated_at,
            metadata
        FROM alerts
        WHERE created_at >= %s
        ORDER BY created_at DESC
        LIMIT %s
        """

        cursor.execute(query, (since_time, limit))
        alerts = cursor.fetchall()

        if not alerts:
            print(f"📭 在过去 {hours} 小时内没有发现告警")
            return

        print(f"📊 找到 {len(alerts)} 条告警记录 (过去 {hours} 小时)")
        print()

        # 显示告警详情
        for i, alert in enumerate(alerts, 1):
            alert_id, title, description, status, severity, source, attack_mac, threat_score, created_at, updated_at, metadata = alert

            print(f"🚨 告警 #{i}")
            print(f"   ID: {alert_id}")
            print(f"   标题: {title}")
            print(f"   状态: {status}")
            print(f"   严重程度: {severity}")
            print(f"   来源: {source}")
            print(f"   攻击MAC: {attack_mac or 'N/A'}")
            print(f"   威胁分数: {threat_score or 'N/A'}")
            print(f"   创建时间: {created_at}")
            print(f"   更新时间: {updated_at}")

            if description:
                print(f"   描述: {description[:200]}{'...' if len(description) > 200 else ''}")

            if metadata:
                try:
                    metadata_dict = json.loads(metadata)
                    print(f"   元数据: {json.dumps(metadata_dict, indent=2, ensure_ascii=False)[:500]}{'...' if len(json.dumps(metadata_dict)) > 500 else ''}")
                except:
                    print(f"   元数据: {metadata[:200]}{'...' if len(metadata) > 200 else ''}")

            print("-" * 60)

        # 统计信息
        severity_counts = {}
        status_counts = {}

        for alert in alerts:
            severity = alert[4]  # severity
            status = alert[3]    # status

            severity_counts[severity] = severity_counts.get(severity, 0) + 1
            status_counts[status] = status_counts.get(status, 0) + 1

        print("📈 统计信息:")
        print(f"   按严重程度: {severity_counts}")
        print(f"   按状态: {status_counts}")

    except Exception as e:
        print(f"❌ 数据库操作失败: {e}")
        raise
    finally:
        if 'cursor' in locals():
            cursor.close()
        if 'conn' in locals():
            conn.close()

def fetch_alerts_by_customer(customer_id, hours=24):
    """拉取特定客户的告警信息"""

    # 数据库连接参数
    db_config = {
        'host': 'localhost',
        'port': 5432,
        'database': 'threat_detection',
        'user': 'threat_user',
        'password': 'threat_password'
    }

    try:
        # 连接数据库
        conn = psycopg2.connect(**db_config)
        cursor = conn.cursor()

        print(f"🔍 拉取客户 {customer_id} 的最新告警信息")
        print("=" * 60)

        # 计算时间范围
        since_time = datetime.now() - timedelta(hours=hours)

        # 查询特定客户的告警（通过metadata中的customer_id）
        query = """
        SELECT
            id,
            title,
            description,
            status,
            severity,
            source,
            attack_mac,
            threat_score,
            created_at,
            updated_at,
            metadata
        FROM alerts
        WHERE created_at >= %s
        AND metadata::jsonb ->> 'customer_id' = %s
        ORDER BY created_at DESC
        LIMIT 20
        """

        cursor.execute(query, (since_time, customer_id))
        alerts = cursor.fetchall()

        if not alerts:
            print(f"📭 客户 {customer_id} 在过去 {hours} 小时内没有告警")
            return

        print(f"📊 找到 {len(alerts)} 条告警记录")
        print()

        # 显示告警详情
        for i, alert in enumerate(alerts, 1):
            alert_id, title, description, status, severity, source, attack_mac, threat_score, created_at, updated_at, metadata = alert

            print(f"🚨 告警 #{i}")
            print(f"   ID: {alert_id}")
            print(f"   客户ID: {customer_id}")
            print(f"   标题: {title}")
            print(f"   状态: {status}")
            print(f"   严重程度: {severity}")
            print(f"   来源: {source}")
            print(f"   攻击MAC: {attack_mac or 'N/A'}")
            print(f"   威胁分数: {threat_score or 'N/A'}")
            print(f"   创建时间: {created_at}")

            if description:
                print(f"   描述: {description[:200]}{'...' if len(description) > 200 else ''}")

            print("-" * 60)

    except Exception as e:
        print(f"❌ 数据库操作失败: {e}")
        raise
    finally:
        if 'cursor' in locals():
            cursor.close()
        if 'conn' in locals():
            conn.close()

def main():
    parser = argparse.ArgumentParser(description='拉取最新告警信息')
    parser.add_argument('--hours', type=int, default=24, help='查询过去多少小时的告警 (默认: 24)')
    parser.add_argument('--limit', type=int, default=50, help='最多显示多少条告警 (默认: 50)')
    parser.add_argument('--customer', type=str, help='指定客户ID，只显示该客户的告警')

    args = parser.parse_args()

    if args.customer:
        fetch_alerts_by_customer(args.customer, args.hours)
    else:
        fetch_recent_alerts(args.hours, args.limit)

if __name__ == "__main__":
    main()