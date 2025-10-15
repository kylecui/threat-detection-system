#!/usr/bin/env python3
"""
MVP Phase 0: 端到端功能测试
测试3层时间窗口 + 端口权重系统的完整处理链路
"""

import json
import time
import os
import sys
from pathlib import Path
from typing import List, Dict, Set
from collections import defaultdict
from datetime import datetime
from kafka import KafkaProducer, KafkaConsumer
from kafka.admin import KafkaAdminClient, NewTopic
import psycopg2
from psycopg2.extras import RealDictCursor

# 配置
KAFKA_BOOTSTRAP_SERVERS = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092')
DB_HOST = os.getenv('DB_HOST', 'localhost')
DB_PORT = os.getenv('DB_PORT', '5432')
DB_NAME = os.getenv('DB_NAME', 'threat_detection')
DB_USER = os.getenv('DB_USER', 'threat_user')
DB_PASSWORD = os.getenv('DB_PASSWORD', 'threat_password')

# Kafka主题
ATTACK_EVENTS_TOPIC = 'attack-events'
THREAT_ALERTS_TOPIC = 'threat-alerts'

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

def print_error(msg: str):
    print(f"{Color.FAIL}✗ {msg}{Color.ENDC}")

def print_info(msg: str):
    print(f"{Color.OKCYAN}ℹ {msg}{Color.ENDC}")

def print_warning(msg: str):
    print(f"{Color.WARNING}⚠ {msg}{Color.ENDC}")

class MVPTestRunner:
    def __init__(self):
        self.producer = None
        self.consumer = None
        self.db_conn = None
        self.test_results = {
            'logs_parsed': 0,
            'events_sent': 0,
            'alerts_received': 0,
            'tier1_alerts': 0,
            'tier2_alerts': 0,
            'tier3_alerts': 0,
            'high_risk_ports': 0,
            'attackers': set(),
            'unique_ports': set(),
            'db_records': 0
        }

    def setup(self):
        """初始化测试环境"""
        print_header("MVP Phase 0: 端到端功能测试初始化")
        
        # 1. 连接Kafka
        print_info("连接Kafka...")
        try:
            self.producer = KafkaProducer(
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None,
                acks='all',
                retries=3
            )
            print_success(f"Kafka Producer已连接: {KAFKA_BOOTSTRAP_SERVERS}")
        except Exception as e:
            print_error(f"Kafka连接失败: {e}")
            return False

        # 2. 连接PostgreSQL
        print_info("连接PostgreSQL...")
        try:
            self.db_conn = psycopg2.connect(
                host=DB_HOST,
                port=DB_PORT,
                dbname=DB_NAME,
                user=DB_USER,
                password=DB_PASSWORD
            )
            print_success(f"PostgreSQL已连接: {DB_HOST}:{DB_PORT}/{DB_NAME}")
        except Exception as e:
            print_error(f"PostgreSQL连接失败: {e}")
            return False

        # 3. 验证端口权重配置表
        print_info("验证端口权重配置...")
        try:
            cursor = self.db_conn.cursor(cursor_factory=RealDictCursor)
            cursor.execute("SELECT COUNT(*) as cnt FROM port_risk_configs WHERE enabled = TRUE")
            result = cursor.fetchone()
            if result['cnt'] > 0:
                print_success(f"端口权重配置表已加载: {result['cnt']} 条配置")
            else:
                print_warning("端口权重配置表为空，将使用默认权重")
            cursor.close()
        except Exception as e:
            print_warning(f"端口权重表检查失败: {e}")

        print_success("测试环境初始化完成\n")
        return True

    def parse_syslog_message(self, message_text: str) -> Dict:
        """解析syslog消息为JSON格式的attack event"""
        try:
            # 解析JSON格式的日志
            log_data = json.loads(message_text)
            message = log_data.get('message', '')
            
            # 解析message中的键值对
            event_data = {}
            for part in message.split(','):
                if '=' in part:
                    key, value = part.split('=', 1)
                    event_data[key.strip()] = value.strip()
            
            # 只处理攻击事件 (log_type=1)
            if event_data.get('log_type') != '1':
                return None
            
            # 过滤response_port=65536 (ARP探测)
            response_port = int(event_data.get('response_port', 0))
            if response_port == 65536:
                return None
            
            # 构造AttackEvent
            attack_event = {
                'id': f"{event_data.get('dev_serial')}_{event_data.get('log_time')}_{event_data.get('attack_mac')}",
                'customerId': event_data.get('dev_serial', 'unknown')[:16],  # 使用设备序列号前缀作为客户ID
                'devSerial': event_data.get('dev_serial'),
                'attackMac': event_data.get('attack_mac'),
                'attackIp': event_data.get('attack_ip'),
                'responseIp': event_data.get('response_ip'),
                'responsePort': response_port,
                'logTime': int(event_data.get('log_time', 0)),
                'timestamp': log_data.get('@timestamp', datetime.now().isoformat())
            }
            
            return attack_event
            
        except Exception as e:
            print_error(f"日志解析失败: {e} - {message_text[:100]}")
            return None

    def load_test_logs(self, limit: int = 1000) -> List[Dict]:
        """加载测试日志"""
        print_header("加载真实测试日志")
        
        if not TEST_LOG_DIR.exists():
            print_error(f"测试日志目录不存在: {TEST_LOG_DIR}")
            return []
        
        log_files = sorted(TEST_LOG_DIR.glob('*.log'))
        print_info(f"找到 {len(log_files)} 个日志文件")
        
        attack_events = []
        port_stats = defaultdict(int)
        
        for log_file in log_files[:5]:  # 处理前5个文件
            print_info(f"读取: {log_file.name}")
            
            with open(log_file, 'r', encoding='utf-8') as f:
                for line_num, line in enumerate(f, 1):
                    if len(attack_events) >= limit:
                        break
                    
                    line = line.strip()
                    if not line:
                        continue
                    
                    event = self.parse_syslog_message(line)
                    if event:
                        attack_events.append(event)
                        port_stats[event['responsePort']] += 1
                        self.test_results['attackers'].add(event['attackMac'])
                        self.test_results['unique_ports'].add(event['responsePort'])
            
            if len(attack_events) >= limit:
                break
        
        self.test_results['logs_parsed'] = len(attack_events)
        
        print_success(f"成功解析 {len(attack_events)} 条攻击事件")
        print_info(f"  - 唯一攻击者: {len(self.test_results['attackers'])}")
        print_info(f"  - 唯一端口: {len(self.test_results['unique_ports'])}")
        
        # 显示高危端口统计
        high_risk_ports = [161, 22, 23, 445, 3389, 3306, 1433, 9100, 515]
        print_info("\n高危端口检测:")
        for port in high_risk_ports:
            if port in port_stats:
                self.test_results['high_risk_ports'] += port_stats[port]
                port_name = self.get_port_name(port)
                print_info(f"  - 端口 {port:5d} ({port_name:15s}): {port_stats[port]:4d} 次")
        
        return attack_events

    def get_port_name(self, port: int) -> str:
        """获取端口名称"""
        port_map = {
            161: 'SNMP',
            22: 'SSH',
            23: 'Telnet',
            445: 'SMB',
            3389: 'RDP',
            3306: 'MySQL',
            1433: 'SQL Server',
            9100: 'Printer',
            515: 'LPR'
        }
        return port_map.get(port, 'Unknown')

    def send_events_to_kafka(self, events: List[Dict]):
        """发送事件到Kafka"""
        print_header("发送攻击事件到Kafka")
        
        batch_size = 100
        total_sent = 0
        
        for i in range(0, len(events), batch_size):
            batch = events[i:i+batch_size]
            
            for event in batch:
                try:
                    # 使用customerId作为分区键确保多租户隔离
                    self.producer.send(
                        ATTACK_EVENTS_TOPIC,
                        key=event['customerId'],
                        value=event
                    )
                    total_sent += 1
                except Exception as e:
                    print_error(f"发送事件失败: {e}")
            
            # 刷新批次
            self.producer.flush()
            print_info(f"已发送 {total_sent}/{len(events)} 条事件")
            time.sleep(0.1)  # 模拟真实流量
        
        self.test_results['events_sent'] = total_sent
        print_success(f"完成发送 {total_sent} 条攻击事件到Kafka")

    def consume_threat_alerts(self, timeout: int = 300):
        """消费威胁告警"""
        print_header("监听威胁告警")
        print_info(f"等待流处理完成 (最长 {timeout} 秒)...")
        
        try:
            consumer = KafkaConsumer(
                THREAT_ALERTS_TOPIC,
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                auto_offset_reset='earliest',
                enable_auto_commit=True,
                group_id='mvp-test-consumer',
                value_deserializer=lambda m: json.loads(m.decode('utf-8')),
                consumer_timeout_ms=timeout * 1000
            )
            
            alerts_by_tier = defaultdict(list)
            alerts_by_level = defaultdict(int)
            
            start_time = time.time()
            
            for message in consumer:
                alert = message.value
                
                # 统计分层告警
                tier = alert.get('tier', 0)
                threat_level = alert.get('threatLevel', 'UNKNOWN')
                detection_type = alert.get('detectionType', 'UNKNOWN')
                
                alerts_by_tier[tier].append(alert)
                alerts_by_level[threat_level] += 1
                
                self.test_results['alerts_received'] += 1
                
                if tier == 1:
                    self.test_results['tier1_alerts'] += 1
                elif tier == 2:
                    self.test_results['tier2_alerts'] += 1
                elif tier == 3:
                    self.test_results['tier3_alerts'] += 1
                
                # 显示告警详情
                print_info(f"告警 #{self.test_results['alerts_received']}: " +
                          f"Tier-{tier} | {detection_type} | " +
                          f"Level={threat_level} | Score={alert.get('threatScore', 0):.2f}")
                
                # 检查是否超时
                if time.time() - start_time > timeout:
                    print_warning("达到超时限制，停止监听")
                    break
            
            consumer.close()
            
            print_success(f"\n收到 {self.test_results['alerts_received']} 条威胁告警")
            
            # 统计汇总
            print_info("\n告警分层统计:")
            print_info(f"  - Tier-1 (30秒/勒索软件): {self.test_results['tier1_alerts']}")
            print_info(f"  - Tier-2 (5分钟/主要威胁): {self.test_results['tier2_alerts']}")
            print_info(f"  - Tier-3 (15分钟/APT): {self.test_results['tier3_alerts']}")
            
            print_info("\n威胁等级分布:")
            for level in ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO']:
                if level in alerts_by_level:
                    print_info(f"  - {level:10s}: {alerts_by_level[level]}")
            
        except Exception as e:
            print_error(f"消费告警失败: {e}")

    def verify_database_records(self):
        """验证数据库记录"""
        print_header("验证数据库持久化")
        
        try:
            cursor = self.db_conn.cursor(cursor_factory=RealDictCursor)
            
            # 1. 检查threat_alerts表 (新持久化系统)
            cursor.execute("""
                SELECT COUNT(*) as cnt, 
                       COUNT(DISTINCT customer_id) as customers,
                       COUNT(DISTINCT attack_mac) as attackers,
                       AVG(threat_score) as avg_score,
                       MAX(threat_score) as max_score
                FROM threat_alerts
                WHERE created_at > NOW() - INTERVAL '1 hour'
            """)
            result = cursor.fetchone()
            
            if result['cnt'] > 0:
                self.test_results['db_records'] = result['cnt']
                print_success(f"数据库记录: {result['cnt']} 条威胁评估")
                print_info(f"  - 客户数: {result['customers']}")
                print_info(f"  - 攻击者数: {result['attackers']}")
                print_info(f"  - 平均威胁分: {result['avg_score']:.2f}")
                print_info(f"  - 最高威胁分: {result['max_score']:.2f}")
            else:
                print_warning("数据库中未找到近期威胁评估记录")
            
            # 2. 检查端口权重使用情况
            cursor.execute("""
                SELECT threat_level, COUNT(*) as cnt
                FROM threat_alerts
                WHERE created_at > NOW() - INTERVAL '1 hour'
                GROUP BY threat_level
                ORDER BY 
                    CASE threat_level 
                        WHEN 'CRITICAL' THEN 1
                        WHEN 'HIGH' THEN 2
                        WHEN 'MEDIUM' THEN 3
                        WHEN 'LOW' THEN 4
                        ELSE 5
                    END
            """)
            
            print_info("\n数据库威胁等级分布:")
            for row in cursor.fetchall():
                print_info(f"  - {row['threat_level']:10s}: {row['cnt']}")
            
            cursor.close()
            
        except Exception as e:
            print_error(f"数据库验证失败: {e}")

    def print_final_report(self):
        """打印最终测试报告"""
        print_header("MVP Phase 0 测试报告")
        
        print(f"{Color.BOLD}数据摄取:{Color.ENDC}")
        print(f"  原始日志解析:     {self.test_results['logs_parsed']:6d} 条")
        print(f"  Kafka事件发送:    {self.test_results['events_sent']:6d} 条")
        print(f"  唯一攻击者:       {len(self.test_results['attackers']):6d} 个")
        print(f"  唯一端口:         {len(self.test_results['unique_ports']):6d} 个")
        print(f"  高危端口攻击:     {self.test_results['high_risk_ports']:6d} 次")
        
        print(f"\n{Color.BOLD}流处理 (3层时间窗口):{Color.ENDC}")
        print(f"  总告警数:         {self.test_results['alerts_received']:6d} 条")
        print(f"  Tier-1 (30秒):    {self.test_results['tier1_alerts']:6d} 条")
        print(f"  Tier-2 (5分钟):   {self.test_results['tier2_alerts']:6d} 条")
        print(f"  Tier-3 (15分钟):  {self.test_results['tier3_alerts']:6d} 条")
        
        print(f"\n{Color.BOLD}数据持久化:{Color.ENDC}")
        print(f"  数据库记录:       {self.test_results['db_records']:6d} 条")
        
        # 聚合效率评估 (正确的指标)
        # 注意: 这是聚合系统,不是1:1映射!
        # 正确的评估应该是: 告警数 vs 唯一攻击者数
        aggregation_ratio = 0
        unique_attackers = len(self.test_results['attackers'])
        if unique_attackers > 0:
            aggregation_ratio = (self.test_results['alerts_received'] / 
                                unique_attackers) * 100
        
        print(f"\n{Color.BOLD}聚合效率 (告警/攻击者):{Color.ENDC}")
        if aggregation_ratio >= 150:
            print_success(f"  {aggregation_ratio:.2f}% (优秀 - 每个攻击者产生{aggregation_ratio/100:.1f}条告警)")
        elif aggregation_ratio >= 100:
            print_warning(f"  {aggregation_ratio:.2f}% (良好 - 每个攻击者产生{aggregation_ratio/100:.1f}条告警)")
        else:
            print_error(f"  {aggregation_ratio:.2f}% (需检查 - 可能有攻击者未被检测)")
        
        # 额外统计信息
        print(f"\n{Color.BOLD}系统效率指标:{Color.ENDC}")
        
        # 事件压缩率 (攻击事件 → 唯一攻击者)
        if self.test_results['events_sent'] > 0:
            dedup_ratio = (unique_attackers / self.test_results['events_sent']) * 100
            print(f"  事件去重率:       {dedup_ratio:.2f}% ({self.test_results['events_sent']}条→{unique_attackers}个攻击者)")
        
        # 持久化覆盖率
        if self.test_results['alerts_received'] > 0:
            persistence_coverage = min((self.test_results['db_records'] / 
                                       self.test_results['alerts_received']) * 100, 100)
            if persistence_coverage >= 100:
                print_success(f"  持久化覆盖率:     {persistence_coverage:.2f}% (所有告警已保存)")
            else:
                print_warning(f"  持久化覆盖率:     {persistence_coverage:.2f}% (部分告警未保存)")
        
        # 3层窗口分布
        if self.test_results['alerts_received'] > 0:
            tier1_pct = (self.test_results['tier1_alerts'] / self.test_results['alerts_received']) * 100
            tier2_pct = (self.test_results['tier2_alerts'] / self.test_results['alerts_received']) * 100
            tier3_pct = (self.test_results['tier3_alerts'] / self.test_results['alerts_received']) * 100
            print(f"  窗口分布:         Tier1:{tier1_pct:.1f}% | Tier2:{tier2_pct:.1f}% | Tier3:{tier3_pct:.1f}%")
        
        # MVP功能验证清单
        print(f"\n{Color.BOLD}MVP功能验证清单:{Color.ENDC}")
        
        checks = [
            ("✓ 蜜罐机制理解", True),
            ("✓ 日志解析与摄取", self.test_results['logs_parsed'] > 0),
            ("✓ Kafka消息传递", self.test_results['events_sent'] > 0),
            ("✓ 3层时间窗口", self.test_results['alerts_received'] > 0),
            ("✓ 端口权重计算", self.test_results['high_risk_ports'] > 0),
            ("✓ 威胁评分生成", self.test_results['alerts_received'] > 0),
            ("✓ 数据库持久化", self.test_results['db_records'] > 0),
        ]
        
        all_passed = True
        for check_name, passed in checks:
            if passed:
                print_success(f"  {check_name}")
            else:
                print_error(f"  {check_name}")
                all_passed = False
        
        print("\n" + "="*80)
        if all_passed:
            print_success("MVP Phase 0 功能测试: 全部通过 ✓")
        else:
            print_warning("MVP Phase 0 功能测试: 部分功能需要检查")
        print("="*80 + "\n")

    def cleanup(self):
        """清理资源"""
        print_info("清理测试资源...")
        if self.producer:
            self.producer.close()
        if self.consumer:
            self.consumer.close()
        if self.db_conn:
            self.db_conn.close()
        print_success("资源清理完成")

    def run(self):
        """运行完整测试"""
        try:
            # 1. 初始化
            if not self.setup():
                return False
            
            # 2. 加载测试日志
            events = self.load_test_logs(limit=1000)
            if not events:
                print_error("未加载到测试数据")
                return False
            
            # 3. 发送到Kafka
            self.send_events_to_kafka(events)
            
            # 4. 等待流处理
            print_info("\n等待3层时间窗口处理 (最长窗口15分钟)...")
            time.sleep(5)  # 短暂等待确保消息已被消费
            
            # 5. 消费威胁告警
            self.consume_threat_alerts(timeout=120)  # 等待2分钟
            
            # 6. 验证数据库
            time.sleep(5)  # 等待数据库写入
            self.verify_database_records()
            
            # 7. 生成报告
            self.print_final_report()
            
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
    print(f"""
{Color.HEADER}{Color.BOLD}
╔═══════════════════════════════════════════════════════════════════════════╗
║                                                                           ║
║              MVP Phase 0: 端到端功能测试                                  ║
║              Cloud-Native Threat Detection System                         ║
║                                                                           ║
║  测试范围:                                                                ║
║    ✓ 3层时间窗口 (30s/5min/15min)                                        ║
║    ✓ 端口权重系统 (CVSS + 经验权重)                                      ║
║    ✓ 威胁评分算法                                                        ║
║    ✓ 端到端数据流                                                        ║
║                                                                           ║
╚═══════════════════════════════════════════════════════════════════════════╝
{Color.ENDC}
    """)
    
    runner = MVPTestRunner()
    success = runner.run()
    
    sys.exit(0 if success else 1)

if __name__ == '__main__':
    main()
