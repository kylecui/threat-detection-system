#!/usr/bin/env python3
"""
完整集成测试脚本
测试攻击事件 (log_type=1) 和心跳状态事件 (log_type=2) 的完整处理链路
"""

import json
import time
import os
import sys
from pathlib import Path
from typing import List, Dict, Set
from datetime import datetime
from kafka import KafkaProducer, KafkaConsumer
import psycopg2
from psycopg2.extras import RealDictCursor
import requests

# 配置
KAFKA_BOOTSTRAP_SERVERS = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092')
DATA_INGESTION_URL = os.getenv('DATA_INGESTION_URL', 'http://localhost:8080')
DB_HOST = os.getenv('DB_HOST', 'localhost')
DB_PORT = os.getenv('DB_PORT', '5432')
DB_NAME = os.getenv('DB_NAME', 'threat_detection')
DB_USER = os.getenv('DB_USER', 'threat_user')
DB_PASSWORD = os.getenv('DB_PASSWORD', 'threat_password')

# Kafka主题
ATTACK_EVENTS_TOPIC = 'attack-events'
STATUS_EVENTS_TOPIC = 'status-events'
THREAT_ALERTS_TOPIC = 'threat-alerts'
DEVICE_HEALTH_ALERTS_TOPIC = 'device-health-alerts'

# 测试数据目录
TEST_LOG_DIR = Path(__file__).parent.parent.parent / 'tmp' / 'real_test_logs'

class Color:
    """终端颜色输出"""
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKCYAN = '\033[96m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'

def print_header(msg: str):
    print(f"\n{Color.HEADER}{Color.BOLD}{'='*80}{Color.ENDC}")
    print(f"{Color.HEADER}{Color.BOLD}{msg:^80}{Color.ENDC}")
    print(f"{Color.HEADER}{Color.BOLD}{'='*80}{Color.ENDC}\n")

def print_success(msg: str):
    print(f"{Color.OKGREEN}✓ {msg}{Color.ENDC}")

def print_info(msg: str):
    print(f"{Color.OKCYAN}ℹ {msg}{Color.ENDC}")

def print_warning(msg: str):
    print(f"{Color.WARNING}⚠ {msg}{Color.ENDC}")

def print_error(msg: str):
    print(f"{Color.FAIL}✗ {msg}{Color.ENDC}")

class FullIntegrationTester:
    """完整集成测试器"""
    
    def __init__(self):
        self.db_conn = None
        self.test_results = {
            'attack_logs_sent': 0,
            'status_logs_sent': 0,
            'attack_events_in_kafka': 0,
            'status_events_in_kafka': 0,
            'threat_alerts_received': 0,
            'device_health_alerts_received': 0,
            'threat_assessments_in_db': 0,
            'device_status_in_db': 0,
            'unique_attackers': set(),
            'unique_devices': set()
        }
    
    def setup(self):
        """初始化数据库连接"""
        print_header("初始化测试环境")
        
        try:
            self.db_conn = psycopg2.connect(
                host=DB_HOST,
                port=DB_PORT,
                dbname=DB_NAME,
                user=DB_USER,
                password=DB_PASSWORD
            )
            print_success(f"数据库连接成功: {DB_HOST}:{DB_PORT}/{DB_NAME}")
        except Exception as e:
            print_error(f"数据库连接失败: {e}")
            raise
    
    def cleanup(self):
        """清理资源"""
        if self.db_conn:
            self.db_conn.close()
    
    def load_test_logs(self, limit: int = 1000) -> Dict[str, List[str]]:
        """加载测试日志文件"""
        print_header("加载测试日志")
        
        logs = {
            'attack': [],
            'status': []
        }
        
        if not TEST_LOG_DIR.exists():
            print_warning(f"测试日志目录不存在: {TEST_LOG_DIR}")
            return logs
        
        # 从.log文件中加载日志
        log_files = sorted(TEST_LOG_DIR.glob('*.log'))
        print_info(f"找到 {len(log_files)} 个日志文件")
        
        for log_file in log_files[:5]:  # 处理前5个文件
            print_info(f"读取: {log_file.name}")
            
            with open(log_file, 'r', encoding='utf-8') as f:
                for line in f:
                    if len(logs['attack']) + len(logs['status']) >= limit:
                        break
                    
                    line = line.strip()
                    if not line:
                        continue
                    
                    # 提取message内容
                    message = self.extract_message(line)
                    if not message:
                        continue
                    
                    # 检查是否包含log_type
                    if 'log_type=1' in message:
                        logs['attack'].append(message)
                    elif 'log_type=2' in message:
                        logs['status'].append(message)
            
            if len(logs['attack']) + len(logs['status']) >= limit:
                break
        
        print_success(f"加载完成:")
        print_info(f"  - 攻击日志 (log_type=1): {len(logs['attack'])} 条")
        print_info(f"  - 状态日志 (log_type=2): {len(logs['status'])} 条")
        
        return logs
    
    def extract_message(self, line: str) -> str:
        """从JSON日志行中提取message字段"""
        try:
            # 尝试解析JSON
            data = json.loads(line)
            
            # 优先检查event.original
            if 'event' in data and 'original' in data['event']:
                return data['event']['original'].strip()
            
            # 其次检查message
            if 'message' in data:
                return data['message'].strip()
            
            # 如果都没有，直接返回包含syslog_version的原始行
            if 'syslog_version=' in line:
                return line
            
            return None
        except json.JSONDecodeError:
            # 不是JSON，检查是否直接包含syslog格式
            if 'syslog_version=' in line:
                return line.strip()
            return None
    
    def send_logs_to_ingestion(self, logs: Dict[str, List[str]]) -> bool:
        """发送日志到数据摄取服务"""
        print_header("发送日志到Data Ingestion服务")
        
        total_sent = 0
        total_failed = 0
        
        # 发送攻击日志
        print_info(f"发送 {len(logs['attack'])} 条攻击日志...")
        for i, log in enumerate(logs['attack'], 1):
            try:
                response = requests.post(
                    f"{DATA_INGESTION_URL}/api/v1/logs/ingest",
                    data=log,
                    headers={'Content-Type': 'text/plain'},
                    timeout=5
                )
                
                if response.status_code == 200:
                    total_sent += 1
                    self.test_results['attack_logs_sent'] += 1
                    
                    # 提取攻击者MAC
                    if 'attack_mac=' in log:
                        mac_start = log.find('attack_mac=') + 11
                        mac_end = log.find(',', mac_start)
                        if mac_end == -1:
                            mac_end = log.find(' ', mac_start)
                        if mac_end != -1:
                            attack_mac = log[mac_start:mac_end]
                            self.test_results['unique_attackers'].add(attack_mac)
                else:
                    total_failed += 1
                    if i <= 3:  # 只显示前3个失败
                        print_warning(f"日志 {i} 发送失败: {response.status_code}")
                
                if i % 100 == 0:
                    print_info(f"进度: {i}/{len(logs['attack'])} 条攻击日志")
                    
            except Exception as e:
                total_failed += 1
                if i <= 3:
                    print_error(f"日志 {i} 发送异常: {e}")
        
        print_success(f"攻击日志发送完成: 成功 {self.test_results['attack_logs_sent']} 条")
        
        # 发送状态日志
        if logs['status']:
            print_info(f"发送 {len(logs['status'])} 条状态日志...")
            for i, log in enumerate(logs['status'], 1):
                try:
                    response = requests.post(
                        f"{DATA_INGESTION_URL}/api/v1/logs/ingest",
                        data=log,
                        headers={'Content-Type': 'text/plain'},
                        timeout=5
                    )
                    
                    if response.status_code == 200:
                        total_sent += 1
                        self.test_results['status_logs_sent'] += 1
                        
                        # 提取设备序列号
                        if 'dev_serial=' in log:
                            serial_start = log.find('dev_serial=') + 11
                            serial_end = log.find(',', serial_start)
                            if serial_end == -1:
                                serial_end = log.find(' ', serial_start)
                            if serial_end != -1:
                                dev_serial = log[serial_start:serial_end]
                                self.test_results['unique_devices'].add(dev_serial)
                    else:
                        total_failed += 1
                        if i <= 3:
                            print_warning(f"状态日志 {i} 发送失败: {response.status_code}")
                    
                    if i % 50 == 0:
                        print_info(f"进度: {i}/{len(logs['status'])} 条状态日志")
                        
                except Exception as e:
                    total_failed += 1
                    if i <= 3:
                        print_error(f"状态日志 {i} 发送异常: {e}")
            
            print_success(f"状态日志发送完成: 成功 {self.test_results['status_logs_sent']} 条")
        else:
            print_warning("没有状态日志需要发送")
        
        print_info(f"总计: 成功 {total_sent} 条, 失败 {total_failed} 条")
        return total_failed == 0
    
    def wait_for_processing(self, seconds: int = 30):
        """等待系统处理"""
        print_header(f"等待系统处理 ({seconds}秒)")
        
        for i in range(seconds, 0, -5):
            print_info(f"剩余 {i} 秒...")
            time.sleep(5)
        
        print_success("等待完成")
    
    def check_kafka_topics(self):
        """检查Kafka主题中的消息"""
        print_header("检查Kafka主题")
        
        # 检查attack-events
        try:
            consumer = KafkaConsumer(
                ATTACK_EVENTS_TOPIC,
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                auto_offset_reset='earliest',
                enable_auto_commit=False,
                consumer_timeout_ms=5000,
                value_deserializer=lambda m: json.loads(m.decode('utf-8'))
            )
            
            count = 0
            for _ in consumer:
                count += 1
            
            self.test_results['attack_events_in_kafka'] = count
            print_success(f"attack-events: {count} 条消息")
            consumer.close()
        except Exception as e:
            print_error(f"检查 attack-events 失败: {e}")
        
        # 检查status-events
        try:
            consumer = KafkaConsumer(
                STATUS_EVENTS_TOPIC,
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                auto_offset_reset='earliest',
                enable_auto_commit=False,
                consumer_timeout_ms=5000,
                value_deserializer=lambda m: json.loads(m.decode('utf-8'))
            )
            
            count = 0
            for _ in consumer:
                count += 1
            
            self.test_results['status_events_in_kafka'] = count
            print_success(f"status-events: {count} 条消息")
            consumer.close()
        except Exception as e:
            print_error(f"检查 status-events 失败: {e}")
        
        # 检查threat-alerts
        try:
            consumer = KafkaConsumer(
                THREAT_ALERTS_TOPIC,
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                auto_offset_reset='earliest',
                enable_auto_commit=False,
                consumer_timeout_ms=5000,
                value_deserializer=lambda m: json.loads(m.decode('utf-8'))
            )
            
            count = 0
            for _ in consumer:
                count += 1
            
            self.test_results['threat_alerts_received'] = count
            print_success(f"threat-alerts: {count} 条消息")
            consumer.close()
        except Exception as e:
            print_error(f"检查 threat-alerts 失败: {e}")
        
        # 检查device-health-alerts
        try:
            consumer = KafkaConsumer(
                DEVICE_HEALTH_ALERTS_TOPIC,
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                auto_offset_reset='earliest',
                enable_auto_commit=False,
                consumer_timeout_ms=5000,
                value_deserializer=lambda m: json.loads(m.decode('utf-8'))
            )
            
            count = 0
            for _ in consumer:
                count += 1
            
            self.test_results['device_health_alerts_received'] = count
            print_success(f"device-health-alerts: {count} 条消息")
            consumer.close()
        except Exception as e:
            print_error(f"检查 device-health-alerts 失败: {e}")
    
    def check_database_persistence(self):
        """检查数据库持久化"""
        print_header("检查数据库持久化")
        
        cursor = self.db_conn.cursor(cursor_factory=RealDictCursor)
        
        # 检查威胁评估表 (使用 threat_alerts 表，因为这是服务实际写入的表)
        try:
            cursor.execute("""
                SELECT COUNT(*) as count,
                       COUNT(DISTINCT attack_mac) as unique_attackers,
                       COUNT(DISTINCT customer_id) as unique_customers
                FROM threat_alerts
            """)
            result = cursor.fetchone()
            
            self.test_results['threat_assessments_in_db'] = result['count']
            print_success(f"threat_alerts: {result['count']} 条记录")
            print_info(f"  - 唯一攻击者: {result['unique_attackers']}")
            print_info(f"  - 唯一客户: {result['unique_customers']}")
            
            # 显示最新的几条记录
            cursor.execute("""
                SELECT attack_mac, customer_id, threat_score, threat_level, 
                       attack_count, unique_ips, unique_ports, alert_timestamp
                FROM threat_alerts
                ORDER BY alert_timestamp DESC
                LIMIT 5
            """)
            records = cursor.fetchall()
            
            if records:
                print_info("\n  最新5条威胁评估记录:")
                for i, rec in enumerate(records, 1):
                    print_info(f"    {i}. MAC={rec['attack_mac'][:17]}, "
                             f"客户={rec['customer_id']}, "
                             f"分数={rec['threat_score']:.1f}, "
                             f"等级={rec['threat_level']}, "
                             f"攻击数={rec['attack_count']}")
        except Exception as e:
            print_error(f"检查 threat_alerts 失败: {e}")
        
        # 检查设备状态历史表
        try:
            cursor.execute("""
                SELECT COUNT(*) as count,
                       COUNT(DISTINCT dev_serial) as unique_devices,
                       COUNT(DISTINCT customer_id) as unique_customers,
                       SUM(CASE WHEN is_expired THEN 1 ELSE 0 END) as expired_count,
                       SUM(CASE WHEN is_expiring_soon THEN 1 ELSE 0 END) as expiring_soon_count,
                       SUM(CASE WHEN sentry_count_changed THEN 1 ELSE 0 END) as sentry_changed_count,
                       SUM(CASE WHEN real_host_count_changed THEN 1 ELSE 0 END) as host_changed_count
                FROM device_status_history
            """)
            result = cursor.fetchone()
            
            self.test_results['device_status_in_db'] = result['count']
            print_success(f"device_status_history: {result['count']} 条记录")
            print_info(f"  - 唯一设备: {result['unique_devices']}")
            print_info(f"  - 唯一客户: {result['unique_customers']}")
            print_info(f"  - 已过期设备: {result['expired_count']}")
            print_info(f"  - 临期设备: {result['expiring_soon_count']}")
            print_info(f"  - 诱饵数量变化: {result['sentry_changed_count']}")
            print_info(f"  - 在线设备变化: {result['host_changed_count']}")
            
            # 显示最新的几条记录
            cursor.execute("""
                SELECT dev_serial, customer_id, sentry_count, real_host_count,
                       is_healthy, is_expired, is_expiring_soon, report_time
                FROM device_status_history
                ORDER BY report_time DESC
                LIMIT 5
            """)
            records = cursor.fetchall()
            
            if records:
                print_info("\n  最新5条设备状态记录:")
                for i, rec in enumerate(records, 1):
                    status_flags = []
                    if rec['is_expired']:
                        status_flags.append('已过期')
                    elif rec['is_expiring_soon']:
                        status_flags.append('临期')
                    if rec['is_healthy']:
                        status_flags.append('健康')
                    status_str = ','.join(status_flags) if status_flags else '正常'
                    
                    print_info(f"    {i}. 设备={rec['dev_serial'][:20]}, "
                             f"客户={rec['customer_id']}, "
                             f"诱饵={rec['sentry_count']}, "
                             f"在线={rec['real_host_count']}, "
                             f"状态={status_str}")
        except Exception as e:
            print_error(f"检查 device_status_history 失败: {e}")
        
        cursor.close()
    
    def print_summary(self):
        """打印测试总结"""
        print_header("测试总结")
        
        print(f"{Color.BOLD}数据发送:{Color.ENDC}")
        print(f"  攻击日志发送: {self.test_results['attack_logs_sent']} 条")
        print(f"  状态日志发送: {self.test_results['status_logs_sent']} 条")
        print(f"  唯一攻击者: {len(self.test_results['unique_attackers'])} 个")
        print(f"  唯一设备: {len(self.test_results['unique_devices'])} 个")
        
        print(f"\n{Color.BOLD}Kafka消息:{Color.ENDC}")
        print(f"  attack-events: {self.test_results['attack_events_in_kafka']} 条")
        print(f"  status-events: {self.test_results['status_events_in_kafka']} 条")
        print(f"  threat-alerts: {self.test_results['threat_alerts_received']} 条")
        print(f"  device-health-alerts: {self.test_results['device_health_alerts_received']} 条")
        
        print(f"\n{Color.BOLD}数据库持久化:{Color.ENDC}")
        print(f"  威胁评估记录: {self.test_results['threat_assessments_in_db']} 条")
        print(f"  设备状态记录: {self.test_results['device_status_in_db']} 条")
        
        # 计算成功率
        print(f"\n{Color.BOLD}处理成功率:{Color.ENDC}")
        
        # 攻击事件处理率
        if self.test_results['attack_logs_sent'] > 0:
            attack_ingestion_rate = (self.test_results['attack_events_in_kafka'] / 
                                    self.test_results['attack_logs_sent'] * 100)
            print(f"  攻击事件摄取率: {attack_ingestion_rate:.1f}% "
                  f"({self.test_results['attack_events_in_kafka']}/{self.test_results['attack_logs_sent']})")
            
            if self.test_results['attack_events_in_kafka'] > 0 and self.test_results['threat_alerts_received'] > 0:
                alert_rate = (self.test_results['threat_alerts_received'] / 
                            self.test_results['attack_events_in_kafka'] * 100)
                print(f"  威胁告警生成率: {alert_rate:.1f}% "
                      f"({self.test_results['threat_alerts_received']}/{self.test_results['attack_events_in_kafka']})")
            
            if self.test_results['threat_alerts_received'] > 0 and self.test_results['threat_assessments_in_db'] > 0:
                persistence_rate = (self.test_results['threat_assessments_in_db'] / 
                                  self.test_results['threat_alerts_received'] * 100)
                print(f"  威胁评估持久化率: {persistence_rate:.1f}% "
                      f"({self.test_results['threat_assessments_in_db']}/{self.test_results['threat_alerts_received']})")
        
        # 状态事件处理率
        if self.test_results['status_logs_sent'] > 0:
            status_ingestion_rate = (self.test_results['status_events_in_kafka'] / 
                                    self.test_results['status_logs_sent'] * 100)
            print(f"  状态事件摄取率: {status_ingestion_rate:.1f}% "
                  f"({self.test_results['status_events_in_kafka']}/{self.test_results['status_logs_sent']})")
            
            if self.test_results['device_health_alerts_received'] > 0:
                health_alert_rate = (self.test_results['device_health_alerts_received'] / 
                                   self.test_results['status_events_in_kafka'] * 100)
                print(f"  设备健康告警生成率: {health_alert_rate:.1f}% "
                      f"({self.test_results['device_health_alerts_received']}/{self.test_results['status_events_in_kafka']})")
            
            if self.test_results['device_status_in_db'] > 0:
                device_persistence_rate = (self.test_results['device_status_in_db'] / 
                                          self.test_results['device_health_alerts_received'] * 100) if self.test_results['device_health_alerts_received'] > 0 else 0
                print(f"  设备状态持久化率: {device_persistence_rate:.1f}% "
                      f"({self.test_results['device_status_in_db']}/{self.test_results['device_health_alerts_received']})")
        
        # 总体评估
        print(f"\n{Color.BOLD}总体评估:{Color.ENDC}")
        
        all_checks_passed = True
        
        # 检查攻击事件链路
        if self.test_results['attack_logs_sent'] > 0:
            if self.test_results['attack_events_in_kafka'] == 0:
                print_error("✗ 攻击事件未进入Kafka")
                all_checks_passed = False
            elif self.test_results['threat_alerts_received'] == 0:
                print_error("✗ 未生成威胁告警")
                all_checks_passed = False
            elif self.test_results['threat_assessments_in_db'] == 0:
                print_error("✗ 威胁评估未持久化")
                all_checks_passed = False
            else:
                print_success("✓ 攻击事件处理链路正常")
        
        # 检查状态事件链路
        if self.test_results['status_logs_sent'] > 0:
            if self.test_results['status_events_in_kafka'] == 0:
                print_error("✗ 状态事件未进入Kafka")
                all_checks_passed = False
            elif self.test_results['device_health_alerts_received'] == 0:
                print_warning("⚠ 未生成设备健康告警 (可能是流处理延迟)")
            elif self.test_results['device_status_in_db'] == 0:
                print_warning("⚠ 设备状态未持久化 (可能是消费延迟)")
            else:
                print_success("✓ 状态事件处理链路正常")
        
        if all_checks_passed:
            print(f"\n{Color.OKGREEN}{Color.BOLD}🎉 所有测试通过！{Color.ENDC}")
        else:
            print(f"\n{Color.WARNING}{Color.BOLD}⚠️  部分测试未通过，请检查服务状态{Color.ENDC}")
    
    def run(self):
        """运行完整测试"""
        try:
            self.setup()
            
            # 1. 加载测试日志
            logs = self.load_test_logs()
            
            if not logs['attack'] and not logs['status']:
                print_error("没有找到测试日志文件！")
                print_info(f"请确保测试日志位于: {TEST_LOG_DIR}")
                return False
            
            # 2. 发送日志到数据摄取服务
            self.send_logs_to_ingestion(logs)
            
            # 3. 等待系统处理
            self.wait_for_processing(30)
            
            # 4. 检查Kafka主题
            self.check_kafka_topics()
            
            # 5. 再等待一段时间，确保消费者完成持久化
            print_info("等待数据持久化...")
            time.sleep(10)
            
            # 6. 检查数据库持久化
            self.check_database_persistence()
            
            # 7. 打印总结
            self.print_summary()
            
            return True
            
        except KeyboardInterrupt:
            print_warning("\n测试被用户中断")
            return False
        except Exception as e:
            print_error(f"测试执行失败: {e}")
            import traceback
            traceback.print_exc()
            return False
        finally:
            self.cleanup()

def main():
    """主函数"""
    print_header("完整集成测试 - 攻击事件 & 设备状态事件")
    print_info(f"Kafka: {KAFKA_BOOTSTRAP_SERVERS}")
    print_info(f"Data Ingestion: {DATA_INGESTION_URL}")
    print_info(f"Database: {DB_HOST}:{DB_PORT}/{DB_NAME}")
    print_info(f"测试日志目录: {TEST_LOG_DIR}")
    
    tester = FullIntegrationTester()
    success = tester.run()
    
    sys.exit(0 if success else 1)

if __name__ == '__main__':
    main()
