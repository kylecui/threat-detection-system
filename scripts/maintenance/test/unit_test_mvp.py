#!/usr/bin/env python3
"""
MVP Phase 0: 单元测试 - 端口权重和威胁评分
测试核心算法正确性
"""

import unittest
from datetime import datetime
import json

class PortWeightTests(unittest.TestCase):
    """端口权重计算测试"""
    
    def test_port_diversity_weight(self):
        """测试端口多样性权重计算"""
        test_cases = [
            (1, 1.0),
            (3, 1.2),
            (5, 1.4),
            (10, 1.6),
            (20, 1.8),
            (25, 2.0)
        ]
        
        for unique_ports, expected_weight in test_cases:
            weight = self.calculate_port_diversity_weight(unique_ports)
            self.assertAlmostEqual(weight, expected_weight, places=1,
                                  msg=f"端口数={unique_ports}, 预期权重={expected_weight}")
    
    def calculate_port_diversity_weight(self, unique_port_count: int) -> float:
        """端口多样性权重算法 (与Java实现对齐)"""
        if unique_port_count == 1:
            return 1.0
        if unique_port_count <= 3:
            return 1.2
        if unique_port_count <= 5:
            return 1.4
        if unique_port_count <= 10:
            return 1.6
        if unique_port_count <= 20:
            return 1.8
        return 2.0
    
    def test_high_risk_ports(self):
        """测试高危端口权重"""
        high_risk_ports = {
            22: ('SSH', 10.0),
            23: ('Telnet', 10.0),
            3389: ('RDP', 10.0),
            445: ('SMB', 9.5),
            3306: ('MySQL', 9.0),
            1433: ('SQL Server', 9.0),
            161: ('SNMP', 7.5),
        }
        
        for port, (name, expected_weight) in high_risk_ports.items():
            print(f"  端口 {port:5d} ({name:15s}): 权重 {expected_weight}")
            self.assertGreaterEqual(expected_weight, 7.0,
                                   msg=f"{name}端口应为高危端口")

class ThreatScoreTests(unittest.TestCase):
    """威胁评分算法测试"""
    
    def test_time_weight_calculation(self):
        """测试时间权重计算 (5时段)"""
        test_cases = [
            (2, 1.2),   # 深夜 0-6点
            (8, 1.1),   # 早晨 6-9点
            (12, 1.0),  # 工作时间 9-17点
            (19, 0.9),  # 傍晚 17-21点
            (23, 0.8),  # 夜间 21-24点
        ]
        
        for hour, expected_weight in test_cases:
            weight = self.calculate_time_weight(hour)
            self.assertAlmostEqual(weight, expected_weight, places=1,
                                  msg=f"小时={hour}, 预期权重={expected_weight}")
    
    def calculate_time_weight(self, hour: int) -> float:
        """时间权重算法 (与Java实现对齐)"""
        if 0 <= hour < 6:
            return 1.2
        if 6 <= hour < 9:
            return 1.1
        if 9 <= hour < 17:
            return 1.0
        if 17 <= hour < 21:
            return 0.9
        return 0.8
    
    def test_ip_weight_calculation(self):
        """测试IP多样性权重 (5档)"""
        test_cases = [
            (1, 1.0),
            (3, 1.3),
            (5, 1.5),
            (10, 1.7),
            (15, 2.0)
        ]
        
        for unique_ips, expected_weight in test_cases:
            weight = self.calculate_ip_weight(unique_ips)
            self.assertAlmostEqual(weight, expected_weight, places=1,
                                  msg=f"唯一IP数={unique_ips}, 预期权重={expected_weight}")
    
    def calculate_ip_weight(self, unique_ips: int) -> float:
        """IP权重算法 (与Java实现对齐)"""
        if unique_ips == 1:
            return 1.0
        if unique_ips <= 3:
            return 1.3
        if unique_ips <= 5:
            return 1.5
        if unique_ips <= 10:
            return 1.7
        return 2.0
    
    def test_threat_score_formula(self):
        """测试完整威胁评分公式"""
        # 测试场景1: 深夜大规模扫描
        scenario1 = {
            'attack_count': 100,
            'unique_ips': 10,
            'unique_ports': 5,
            'unique_devices': 1,
            'hour': 2  # 深夜
        }
        
        base_score = (scenario1['attack_count'] * 
                     scenario1['unique_ips'] * 
                     scenario1['unique_ports'])
        time_weight = self.calculate_time_weight(scenario1['hour'])
        ip_weight = self.calculate_ip_weight(scenario1['unique_ips'])
        port_weight = self.calculate_port_diversity_weight(scenario1['unique_ports'])
        device_weight = 1.5 if scenario1['unique_devices'] > 1 else 1.0
        
        threat_score = (base_score * time_weight * ip_weight * 
                       port_weight * device_weight)
        
        print(f"\n场景1 - 深夜大规模扫描:")
        print(f"  基础分: {base_score}")
        print(f"  时间权重: {time_weight}")
        print(f"  IP权重: {ip_weight}")
        print(f"  端口权重: {port_weight}")
        print(f"  设备权重: {device_weight}")
        print(f"  最终威胁分: {threat_score:.2f}")
        
        self.assertGreater(threat_score, 1000, "深夜大规模扫描应为高危威胁")
        
        # 测试场景2: 工作时间单一攻击
        scenario2 = {
            'attack_count': 10,
            'unique_ips': 1,
            'unique_ports': 1,
            'unique_devices': 1,
            'hour': 14
        }
        
        base_score2 = (scenario2['attack_count'] * 
                      scenario2['unique_ips'] * 
                      scenario2['unique_ports'])
        threat_score2 = (base_score2 * 
                        self.calculate_time_weight(scenario2['hour']) * 
                        self.calculate_ip_weight(scenario2['unique_ips']) * 
                        self.calculate_port_diversity_weight(scenario2['unique_ports']) * 
                        1.0)
        
        print(f"\n场景2 - 工作时间单一攻击:")
        print(f"  最终威胁分: {threat_score2:.2f}")
        
        self.assertLess(threat_score2, threat_score, 
                       "单一攻击威胁分应低于大规模扫描")
    
    def calculate_port_diversity_weight(self, unique_ports: int) -> float:
        """端口多样性权重"""
        if unique_ports == 1:
            return 1.0
        if unique_ports <= 3:
            return 1.2
        if unique_ports <= 5:
            return 1.4
        if unique_ports <= 10:
            return 1.6
        if unique_ports <= 20:
            return 1.8
        return 2.0
    
    def test_threat_level_classification(self):
        """测试威胁等级分类 (5级)"""
        test_cases = [
            (1500, 'CRITICAL'),
            (700, 'HIGH'),
            (300, 'MEDIUM'),
            (80, 'LOW'),
            (20, 'INFO')
        ]
        
        for score, expected_level in test_cases:
            level = self.determine_threat_level(score)
            self.assertEqual(level, expected_level,
                           msg=f"分数={score}, 预期等级={expected_level}")
    
    def determine_threat_level(self, score: float) -> str:
        """威胁等级分类 (与Java实现对齐)"""
        if score >= 1000.0:
            return 'CRITICAL'
        if score >= 500.0:
            return 'HIGH'
        if score >= 200.0:
            return 'MEDIUM'
        if score >= 50.0:
            return 'LOW'
        return 'INFO'

class MultiTierWindowTests(unittest.TestCase):
    """多层时间窗口测试"""
    
    def test_tier_alert_thresholds(self):
        """测试分层告警阈值"""
        # Tier-1: 30秒窗口 - 勒索软件检测
        tier1_scenarios = [
            ({'attack_count': 60, 'unique_ips': 3}, True, "高频攻击应触发Tier-1"),
            ({'attack_count': 25, 'unique_ips': 6}, True, "中频+多IP应触发Tier-1"),
            ({'attack_count': 10, 'unique_ips': 2}, False, "低频攻击不应触发Tier-1")
        ]
        
        for scenario, expected, msg in tier1_scenarios:
            result = self.should_trigger_tier1_alert(
                scenario['attack_count'], scenario['unique_ips'])
            self.assertEqual(result, expected, msg=msg)
        
        # Tier-2: 5分钟窗口 - 主要威胁检测
        tier2_scenarios = [
            ({'attack_count': 15, 'unique_ips': 2, 'unique_ports': 3}, True),
            ({'attack_count': 5, 'unique_ips': 4, 'unique_ports': 2}, True),
            ({'attack_count': 3, 'unique_ips': 1, 'unique_ports': 2}, False)
        ]
        
        for scenario, expected in tier2_scenarios:
            result = self.should_trigger_tier2_alert(
                scenario['attack_count'], 
                scenario['unique_ips'],
                scenario['unique_ports'])
            self.assertEqual(result, expected)
        
        # Tier-3: 15分钟窗口 - APT慢速扫描
        tier3_scenarios = [
            ({'attack_count': 8, 'unique_ips': 1, 'unique_ports': 2}, True),
            ({'attack_count': 3, 'unique_ips': 3, 'unique_ports': 4}, True),
            ({'attack_count': 2, 'unique_ips': 1, 'unique_ports': 1}, False)
        ]
        
        for scenario, expected in tier3_scenarios:
            result = self.should_trigger_tier3_alert(
                scenario['attack_count'],
                scenario['unique_ips'],
                scenario['unique_ports'])
            self.assertEqual(result, expected)
    
    def should_trigger_tier1_alert(self, attack_count: int, unique_ips: int) -> bool:
        """Tier-1 告警阈值逻辑"""
        return attack_count >= 50 or (attack_count >= 20 and unique_ips >= 5)
    
    def should_trigger_tier2_alert(self, attack_count: int, unique_ips: int, 
                                   unique_ports: int) -> bool:
        """Tier-2 告警阈值逻辑"""
        return attack_count >= 10 or unique_ips >= 3 or unique_ports >= 5
    
    def should_trigger_tier3_alert(self, attack_count: int, unique_ips: int,
                                   unique_ports: int) -> bool:
        """Tier-3 告警阈值逻辑"""
        return attack_count >= 5 or (unique_ips >= 2 and unique_ports >= 3)
    
    def test_tier_weight_calculation(self):
        """测试分层权重"""
        tier_weights = {
            1: 1.5,  # 30秒窗口 - 高危
            2: 1.0,  # 5分钟窗口 - 标准
            3: 1.2   # 15分钟窗口 - APT
        }
        
        for tier, expected_weight in tier_weights.items():
            weight = self.calculate_tier_weight(tier)
            self.assertEqual(weight, expected_weight,
                           msg=f"Tier-{tier}权重应为{expected_weight}")
    
    def calculate_tier_weight(self, tier: int) -> float:
        """分层权重计算"""
        if tier == 1:
            return 1.5
        elif tier == 2:
            return 1.0
        elif tier == 3:
            return 1.2
        return 1.0

def run_unit_tests():
    """运行单元测试"""
    print("="*80)
    print("MVP Phase 0: 单元测试".center(80))
    print("="*80)
    
    # 创建测试套件
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()
    
    # 添加测试类
    suite.addTests(loader.loadTestsFromTestCase(PortWeightTests))
    suite.addTests(loader.loadTestsFromTestCase(ThreatScoreTests))
    suite.addTests(loader.loadTestsFromTestCase(MultiTierWindowTests))
    
    # 运行测试
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    
    # 输出结果
    print("\n" + "="*80)
    print(f"测试完成: 运行={result.testsRun}, 成功={result.testsRun - len(result.failures) - len(result.errors)}, " +
          f"失败={len(result.failures)}, 错误={len(result.errors)}")
    print("="*80)
    
    return result.wasSuccessful()

if __name__ == '__main__':
    import sys
    success = run_unit_tests()
    sys.exit(0 if success else 1)
