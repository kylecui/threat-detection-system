#!/usr/bin/env python3
"""
快速系统状态检查脚本
检查Kafka和数据库的当前状态，不发送新数据
"""

import json
from kafka import KafkaConsumer
import psycopg2
from psycopg2.extras import RealDictCursor

# 配置
KAFKA_BOOTSTRAP_SERVERS = 'localhost:9092'
DB_CONFIG = {
    'host': 'localhost',
    'port': 5432,
    'database': 'threat_detection',
    'user': 'threat_user',
    'password': 'threat_password'
}

def check_kafka():
    """检查Kafka主题状态"""
    print("\n" + "="*80)
    print(" Kafka Topics 状态检查")
    print("="*80 + "\n")
    
    topics = ['attack-events', 'status-events', 'threat-alerts', 'device-health-alerts', 'minute-aggregations']
    
    for topic in topics:
        try:
            consumer = KafkaConsumer(
                topic,
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                auto_offset_reset='earliest',
                enable_auto_commit=False,
                consumer_timeout_ms=3000
            )
            
            count = sum(1 for _ in consumer)
            print(f"✓ {topic:30s}: {count:6d} 条消息")
            consumer.close()
        except Exception as e:
            print(f"✗ {topic:30s}: 错误 - {e}")

def check_database():
    """检查数据库表状态"""
    print("\n" + "="*80)
    print(" PostgreSQL 数据库状态检查")
    print("="*80 + "\n")
    
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        cursor = conn.cursor(cursor_factory=RealDictCursor)
        
        # 检查所有表
        cursor.execute("""
            SELECT table_name 
            FROM information_schema.tables 
            WHERE table_schema = 'public' 
            ORDER BY table_name
        """)
        tables = [row['table_name'] for row in cursor.fetchall()]
        
        print(f"数据库中的表 ({len(tables)} 个):")
        for table in tables:
            try:
                cursor.execute(f"SELECT COUNT(*) as count FROM {table}")
                count = cursor.fetchone()['count']
                print(f"  {table:35s}: {count:8d} 条记录")
            except Exception as e:
                print(f"  {table:35s}: 查询失败 - {e}")
        
        # 检查设备状态历史表（如果存在）
        if 'device_status_history' in tables:
            print("\n设备状态历史详情:")
            cursor.execute("""
                SELECT COUNT(*) as total,
                       COUNT(DISTINCT dev_serial) as unique_devices,
                       SUM(CASE WHEN is_expired THEN 1 ELSE 0 END) as expired,
                       SUM(CASE WHEN is_expiring_soon THEN 1 ELSE 0 END) as expiring_soon
                FROM device_status_history
            """)
            result = cursor.fetchone()
            print(f"  总记录数: {result['total']}")
            print(f"  唯一设备: {result['unique_devices']}")
            print(f"  已过期: {result['expired']}")
            print(f"  临期: {result['expiring_soon']}")
        
        # 检查威胁评估表（如果存在）
        if 'threat_assessments' in tables:
            print("\n威胁评估详情:")
            cursor.execute("""
                SELECT COUNT(*) as total,
                       COUNT(DISTINCT attack_mac) as unique_attackers,
                       AVG(threat_score) as avg_score,
                       MAX(threat_score) as max_score
                FROM threat_assessments
            """)
            result = cursor.fetchone()
            print(f"  总记录数: {result['total']}")
            print(f"  唯一攻击者: {result['unique_attackers']}")
            print(f"  平均威胁分数: {result['avg_score']:.2f}" if result['avg_score'] else "  平均威胁分数: N/A")
            print(f"  最高威胁分数: {result['max_score']:.2f}" if result['max_score'] else "  最高威胁分数: N/A")
        
        cursor.close()
        conn.close()
        
    except Exception as e:
        print(f"✗ 数据库连接失败: {e}")

def main():
    print("\n🔍 系统状态快速检查")
    check_kafka()
    check_database()
    print("\n" + "="*80)
    print(" 检查完成")
    print("="*80 + "\n")

if __name__ == '__main__':
    main()
