#!/usr/bin/env python3
"""
集成测试脚本 - 生成CRITICAL等级威胁事件测试邮件通知
"""

import requests
import json
import time
import sys
import random
from datetime import datetime

# 配置
DATA_INGESTION_URL = "http://localhost:8080/api/v1/logs/ingest"
ALERT_MANAGEMENT_URL = "http://localhost:8082/api/v1/integration-test/stats"

# CRITICAL等级威胁事件样本
CRITICAL_EVENTS = [
    {
        "severity": 4,
        "eventType": "SQL_INJECTION",
        "source": "web-server",
        "message": "检测到高危SQL注入攻击，尝试访问系统核心数据库",
        "metadata": {
            "userAgent": "SQLMap/1.6.5",
            "ip": "192.168.1.100",
            "url": "/api/user/login",
            "payload": "UNION SELECT username,password FROM users--"
        }
    },
    {
        "severity": 5,
        "eventType": "BRUTE_FORCE",
        "source": "auth-service",
        "message": "检测到暴力破解攻击，短时间内大量失败登录尝试",
        "metadata": {
            "userAgent": "Hydra/9.1",
            "ip": "10.0.0.50",
            "attempts": 150,
            "timeWindow": "5分钟",
            "targetUser": "admin"
        }
    },
    {
        "severity": 4,
        "eventType": "XSS_ATTACK",
        "source": "web-application",
        "message": "检测到跨站脚本攻击，恶意脚本注入成功",
        "metadata": {
            "userAgent": "Mozilla/5.0 (XSS Hunter)",
            "ip": "172.16.0.25",
            "url": "/search",
            "payload": "<script>alert('XSS')</script>",
            "impact": "高危"
        }
    },
    {
        "severity": 5,
        "eventType": "RANSOMWARE",
        "source": "file-server",
        "message": "检测到勒索软件活动，文件加密行为",
        "metadata": {
            "userAgent": "WannaCry/2.0",
            "ip": "192.168.1.200",
            "encryptedFiles": 250,
            "ransomNote": "README.txt",
            "extension": ".wncry"
        }
    },
    {
        "severity": 4,
        "eventType": "DDOS_ATTACK",
        "source": "network-gateway",
        "message": "检测到分布式拒绝服务攻击，大量异常流量",
        "metadata": {
            "attackType": "SYN Flood",
            "packetsPerSecond": 50000,
            "sourceIPs": 150,
            "targetPort": 80,
            "duration": "持续中"
        }
    }
]

def send_critical_event(event):
    """发送CRITICAL等级威胁事件"""
    try:
        # 构建完整的syslog格式的日志，包含所有必需字段
        current_time = int(time.time())
        syslog_data = f"syslog_version=1.10.0,dev_serial=threatsensor{random.randint(1000,9999)},log_type=1,sub_type=1,attack_mac={random_mac()},attack_ip={event['metadata']['ip']},response_ip=192.168.1.1,response_port=80,line_id={random.randint(1,1000)},Iface_type=1,Vlan_id=100,log_time={current_time}"

        print(f"发送威胁事件: {event['eventType']} - {event['message'][:50]}...")

        response = requests.post(DATA_INGESTION_URL,
                               data=syslog_data,
                               headers={'Content-Type': 'text/plain'},
                               timeout=10)

        if response.status_code == 200:
            print(f"✅ 事件发送成功: {event['eventType']}")
            return True
        else:
            print(f"❌ 事件发送失败: {response.status_code} - {response.text}")
            return False

    except Exception as e:
        print(f"❌ 发送事件时出错: {e}")
        return False

def random_mac():
    """生成随机MAC地址"""
    return ":".join([f"{random.randint(0,255):02x}" for _ in range(6)])

def check_notification_stats():
    """检查通知统计"""
    try:
        response = requests.get(ALERT_MANAGEMENT_URL, timeout=10)
        if response.status_code == 200:
            stats = response.json()
            print("📊 当前通知统计:")
            print(f"   - 已发送邮件: {stats['emailsSentInCurrentWindow']}")
            print(f"   - 窗口上限: {stats['maxEmailsPerWindow']}")
            print(f"   - 剩余额度: {stats['remainingEmails']}")
            print(f"   - 窗口时长: {stats['windowMinutes']}分钟")
            return stats
        else:
            print(f"❌ 获取统计失败: {response.status_code}")
            return None
    except Exception as e:
        print(f"❌ 获取统计时出错: {e}")
        return None

def wait_for_processing(seconds=30):
    """等待事件处理"""
    print(f"⏳ 等待 {seconds} 秒让系统处理事件...")
    time.sleep(seconds)

def main():
    print("🚀 开始集成测试 - CRITICAL威胁邮件通知")
    print("=" * 50)

    # 检查服务状态
    print("🔍 检查告警管理服务状态...")
    try:
        response = requests.get("http://localhost:8082/api/v1/integration-test/status", timeout=10)
        if response.status_code == 200:
            status = response.json()
            print("✅ 集成测试服务运行正常")
            print(f"   - 邮件接收者: {status['emailRecipient']}")
            print(f"   - 通知规则: {status['notificationRules']}")
        else:
            print("❌ 告警管理服务未就绪，请确保服务正在运行")
            sys.exit(1)
    except Exception as e:
        print(f"❌ 无法连接告警管理服务: {e}")
        print("请确保所有服务都已启动: docker-compose -f docker/docker-compose.yml up -d")
        sys.exit(1)

    print("\n📧 测试邮件通知功能")
    print("注意: 仅CRITICAL等级(severity >= 4)的威胁会触发邮件通知")
    print("频率限制: 每10分钟最多5封邮件")

    # 显示初始统计
    initial_stats = check_notification_stats()

    # 发送多个CRITICAL事件
    events_to_send = 3  # 发送3个事件测试
    print(f"\n🎯 发送 {events_to_send} 个CRITICAL等级威胁事件...")

    for i, event in enumerate(CRITICAL_EVENTS[:events_to_send], 1):
        print(f"\n--- 发送事件 {i}/{events_to_send} ---")
        success = send_critical_event(event)
        if success:
            # 等待事件处理
            wait_for_processing(10)
            # 检查统计更新
            check_notification_stats()
        else:
            print("⚠️  事件发送失败，跳过等待")

    print("\n" + "=" * 50)
    print("🎉 集成测试完成！")
    print("\n📧 请检查邮箱 kylecui@outlook.com 是否收到威胁告警邮件")
    print("📊 最终通知统计:")
    final_stats = check_notification_stats()

    if final_stats and final_stats['emailsSentInCurrentWindow'] > (initial_stats['emailsSentInCurrentWindow'] if initial_stats else 0):
        print("✅ 邮件通知功能测试成功！")
    else:
        print("⚠️  未检测到新的邮件发送，可能原因:")
        print("   - 事件未被正确处理为CRITICAL等级")
        print("   - 已达到10分钟内的邮件发送上限")
        print("   - 邮件服务器配置问题")

    print("\n🔍 故障排除提示:")
    print("1. 检查告警管理服务日志: docker-compose logs -f alert-management")
    print("2. 验证邮件配置是否正确")
    print("3. 确认威胁事件被正确分类为CRITICAL等级")
    print("4. 检查Kafka消息是否正常传递")

if __name__ == "__main__":
    main()