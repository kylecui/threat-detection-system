#!/usr/bin/env python3
"""
设备健康监控功能测试脚本
测试 log_type=2 心跳数据的处理流程
"""

import json
import time
import requests
from kafka import KafkaProducer, KafkaConsumer
from kafka.errors import KafkaError
import psycopg2
from datetime import datetime

# 配置
KAFKA_BOOTSTRAP_SERVERS = 'localhost:9092'
DATA_INGESTION_URL = 'http://localhost:8081'
POSTGRES_CONFIG = {
    'host': 'localhost',
    'port': 5432,
    'database': 'threat_detection',
    'user': 'threat_user',
    'password': 'threat_password'
}

class DeviceHealthTester:
    def __init__(self):
        self.producer = None
        self.consumer = None
        self.db_conn = None
        
    def setup(self):
        """初始化连接"""
        print("🔧 初始化测试环境...")
        
        # Kafka Producer
        self.producer = KafkaProducer(
            bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )
        
        # Kafka Consumer (监听device-health-alerts)
        self.consumer = KafkaConsumer(
            'device-health-alerts',
            bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
            auto_offset_reset='latest',
            enable_auto_commit=True,
            group_id='device-health-test-group',
            value_deserializer=lambda m: json.loads(m.decode('utf-8')),
            consumer_timeout_ms=5000
        )
        
        # 数据库连接
        try:
            self.db_conn = psycopg2.connect(**POSTGRES_CONFIG)
            print("✅ 数据库连接成功")
        except Exception as e:
            print(f"❌ 数据库连接失败: {e}")
            
        print("✅ 测试环境初始化完成\n")
    
    def cleanup(self):
        """清理资源"""
        if self.producer:
            self.producer.close()
        if self.consumer:
            self.consumer.close()
        if self.db_conn:
            self.db_conn.close()
    
    def send_heartbeat_log(self, dev_serial, sentry_count, real_host_count, 
                          dev_start_time, dev_end_time=-1):
        """发送心跳日志到data-ingestion服务"""
        current_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        
        log_message = (
            f"syslog_version=1.10.0,dev_serial={dev_serial},log_type=2,"
            f"sentry_count={sentry_count},real_host_count={real_host_count},"
            f"dev_start_time={dev_start_time},dev_end_time={dev_end_time},"
            f"time={current_time}"
        )
        
        print(f"📤 发送心跳日志: devSerial={dev_serial}, sentry={sentry_count}, "
              f"hosts={real_host_count}, endTime={dev_end_time}")
        
        try:
            response = requests.post(
                f"{DATA_INGESTION_URL}/api/logs/ingest",
                data=log_message,
                headers={'Content-Type': 'text/plain'},
                timeout=5
            )
            
            if response.status_code == 200:
                print(f"✅ 心跳日志发送成功")
                return True
            else:
                print(f"❌ 心跳日志发送失败: {response.status_code} - {response.text}")
                return False
        except Exception as e:
            print(f"❌ 发送失败: {e}")
            return False
    
    def wait_for_health_alert(self, timeout=10):
        """等待设备健康告警"""
        print(f"⏳ 等待设备健康告警 (超时: {timeout}秒)...")
        
        start_time = time.time()
        alerts = []
        
        try:
            for message in self.consumer:
                alerts.append(message.value)
                print(f"✅ 收到健康告警: {json.dumps(message.value, indent=2)}")
                
                if time.time() - start_time > timeout:
                    break
        except Exception as e:
            print(f"⚠️  消费超时或无消息: {e}")
        
        return alerts
    
    def check_database_records(self, dev_serial):
        """检查数据库中的记录"""
        print(f"\n🔍 检查数据库记录: {dev_serial}")
        
        try:
            cursor = self.db_conn.cursor()
            
            # 查询最新记录
            cursor.execute("""
                SELECT dev_serial, customer_id, sentry_count, real_host_count,
                       dev_start_time, dev_end_time, is_healthy, is_expired, 
                       is_expiring_soon, report_time
                FROM device_status_history
                WHERE dev_serial = %s
                ORDER BY report_time DESC
                LIMIT 5
            """, (dev_serial,))
            
            records = cursor.fetchall()
            
            if records:
                print(f"✅ 找到 {len(records)} 条记录:")
                for i, record in enumerate(records, 1):
                    print(f"  记录 {i}:")
                    print(f"    设备: {record[0]}, 客户: {record[1]}")
                    print(f"    诱饵数: {record[2]}, 在线设备: {record[3]}")
                    print(f"    启用时间: {record[4]}, 到期时间: {record[5]}")
                    print(f"    健康: {record[6]}, 过期: {record[7]}, 临期: {record[8]}")
                    print(f"    报告时间: {record[9]}")
                return True
            else:
                print(f"❌ 未找到记录")
                return False
                
        except Exception as e:
            print(f"❌ 数据库查询失败: {e}")
            return False
        finally:
            cursor.close()
    
    def run_test_scenarios(self):
        """运行测试场景"""
        print("=" * 70)
        print("开始设备健康监控测试")
        print("=" * 70)
        
        current_epoch = int(time.time())
        
        # 场景1: 正常设备
        print("\n【场景1】正常设备 (长期有效)")
        print("-" * 70)
        self.send_heartbeat_log(
            dev_serial='TEST-DEVICE-001',
            sentry_count=100,
            real_host_count=500,
            dev_start_time=current_epoch - 86400,  # 1天前启用
            dev_end_time=-1  # 长期有效
        )
        time.sleep(3)
        alerts1 = self.wait_for_health_alert(timeout=5)
        time.sleep(2)
        self.check_database_records('TEST-DEVICE-001')
        
        # 场景2: 临近到期设备 (5天后到期)
        print("\n【场景2】临近到期设备 (5天后到期)")
        print("-" * 70)
        self.send_heartbeat_log(
            dev_serial='TEST-DEVICE-002',
            sentry_count=50,
            real_host_count=200,
            dev_start_time=current_epoch - 86400 * 30,  # 30天前启用
            dev_end_time=current_epoch + 86400 * 5  # 5天后到期
        )
        time.sleep(3)
        alerts2 = self.wait_for_health_alert(timeout=5)
        time.sleep(2)
        self.check_database_records('TEST-DEVICE-002')
        
        # 场景3: 已过期设备
        print("\n【场景3】已过期设备")
        print("-" * 70)
        self.send_heartbeat_log(
            dev_serial='TEST-DEVICE-003',
            sentry_count=20,
            real_host_count=100,
            dev_start_time=current_epoch - 86400 * 60,  # 60天前启用
            dev_end_time=current_epoch - 86400 * 10  # 10天前过期
        )
        time.sleep(3)
        alerts3 = self.wait_for_health_alert(timeout=5)
        time.sleep(2)
        self.check_database_records('TEST-DEVICE-003')
        
        # 场景4: 测试状态变化检测
        print("\n【场景4】状态变化检测 (诱饵数量变化)")
        print("-" * 70)
        # 第一次心跳
        self.send_heartbeat_log(
            dev_serial='TEST-DEVICE-004',
            sentry_count=100,
            real_host_count=300,
            dev_start_time=current_epoch - 3600,
            dev_end_time=-1
        )
        time.sleep(3)
        self.wait_for_health_alert(timeout=5)
        time.sleep(2)
        
        # 第二次心跳 (诱饵数量变化)
        print("\n  📊 诱饵数量变化: 100 → 80")
        self.send_heartbeat_log(
            dev_serial='TEST-DEVICE-004',
            sentry_count=80,  # 变化!
            real_host_count=300,
            dev_start_time=current_epoch - 3600,
            dev_end_time=-1
        )
        time.sleep(3)
        self.wait_for_health_alert(timeout=5)
        time.sleep(2)
        self.check_database_records('TEST-DEVICE-004')
        
        # 测试总结
        print("\n" + "=" * 70)
        print("📊 测试总结")
        print("=" * 70)
        print(f"场景1 (正常设备): {len(alerts1)} 条告警")
        print(f"场景2 (临期设备): {len(alerts2)} 条告警")
        print(f"场景3 (过期设备): {len(alerts3)} 条告警")
        print("场景4 (状态变化): 已完成\n")

def main():
    """主函数"""
    tester = DeviceHealthTester()
    
    try:
        tester.setup()
        tester.run_test_scenarios()
        print("\n✅ 所有测试场景执行完成!")
    except KeyboardInterrupt:
        print("\n⚠️  测试被用户中断")
    except Exception as e:
        print(f"\n❌ 测试执行失败: {e}")
        import traceback
        traceback.print_exc()
    finally:
        tester.cleanup()
        print("\n🧹 测试环境已清理")

if __name__ == '__main__':
    main()
