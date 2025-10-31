#!/usr/bin/env python3
"""
基于API的告警信息拉取脚本
通过REST API读取告警信息，模拟前端调用
"""

import requests
import json
import argparse
from datetime import datetime, timedelta
import sys

class AlertAPIClient:
    """告警API客户端"""

    def __init__(self, base_url="http://localhost:8082"):
        self.base_url = base_url.rstrip('/')
        self.session = requests.Session()

    def get_alerts(self, customer_id=None, status=None, severity=None,
                  start_time=None, end_time=None, page=0, size=20,
                  sort_by="createdAt", sort_dir="DESC"):
        """获取告警列表"""

        url = f"{self.base_url}/api/v1/alerts"

        params = {
            'page': page,
            'size': size,
            'sortBy': sort_by,
            'sortDir': sort_dir
        }

        if customer_id:
            params['customerId'] = customer_id
        if status:
            params['status'] = status
        if severity:
            params['severity'] = severity
        if start_time:
            params['startTime'] = start_time.isoformat()
        if end_time:
            params['endTime'] = end_time.isoformat()

        try:
            response = self.session.get(url, params=params, timeout=30)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"❌ API请求失败: {e}")
            return None

    def get_alert_by_id(self, alert_id):
        """根据ID获取单个告警"""

        url = f"{self.base_url}/api/v1/alerts/{alert_id}"

        try:
            response = self.session.get(url, timeout=30)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"❌ 获取告警详情失败: {e}")
            return None

    def get_alert_analytics(self):
        """获取告警统计信息"""

        url = f"{self.base_url}/api/v1/alerts/analytics"

        try:
            response = self.session.get(url, timeout=30)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"❌ 获取统计信息失败: {e}")
            return None

def format_alert(alert):
    """格式化告警显示"""

    alert_id = alert.get('id', 'N/A')
    title = alert.get('title', 'N/A')
    status = alert.get('status', 'N/A')
    severity = alert.get('severity', 'N/A')
    source = alert.get('source', 'N/A')
    attack_mac = alert.get('attackMac', 'N/A')
    threat_score = alert.get('threatScore', 'N/A')
    created_at = alert.get('createdAt', 'N/A')
    description = alert.get('description', '')

    print(f"🚨 告警 #{alert_id}")
    print(f"   标题: {title}")
    print(f"   状态: {status}")
    print(f"   严重程度: {severity}")
    print(f"   来源: {source}")
    print(f"   攻击MAC: {attack_mac}")
    print(f"   威胁分数: {threat_score}")
    print(f"   创建时间: {created_at}")

    if description:
        print(f"   描述: {description[:200]}{'...' if len(description) > 200 else ''}")

    # 解析metadata中的客户信息
    metadata = alert.get('metadata', '')
    if metadata:
        try:
            metadata_dict = json.loads(metadata)
            customer_id = metadata_dict.get('customer_id', 'N/A')
            print(f"   客户ID: {customer_id}")

            # 显示其他metadata信息
            for key, value in metadata_dict.items():
                if key != 'customer_id' and key not in ['attack_ip', 'response_ip', 'response_port']:
                    print(f"   {key}: {value}")
        except:
            print(f"   元数据: {metadata[:200]}{'...' if len(metadata) > 200 else ''}")

    print("-" * 60)

def fetch_recent_alerts_api(hours=24, limit=50, customer_id=None):
    """通过API拉取最近的告警信息"""

    client = AlertAPIClient()

    print("🔍 通过API拉取最新告警信息")
    print("=" * 60)

    # 计算时间范围
    since_time = datetime.now() - timedelta(hours=hours)

    # 首先获取统计信息
    analytics = client.get_alert_analytics()
    if analytics:
        print(f"📊 系统统计信息:")
        print(f"   总告警数: {analytics.get('totalAlerts', 'N/A')}")
        print(f"   未解决告警: {analytics.get('unresolvedAlerts', 'N/A')}")

        by_status = analytics.get('byStatus', {})
        by_severity = analytics.get('bySeverity', {})

        print(f"   按状态: {by_status}")
        print(f"   按严重程度: {by_severity}")
        print()

    # 获取告警列表
    alerts_data = client.get_alerts(
        customer_id=customer_id,
        start_time=since_time,
        page=0,
        size=min(limit, 100)  # API限制最多100条
    )

    if not alerts_data:
        print(f"❌ 无法获取告警数据")
        return

    content = alerts_data.get('content', [])
    total_elements = alerts_data.get('totalElements', 0)
    total_pages = alerts_data.get('totalPages', 0)

    if not content:
        print(f"📭 在过去 {hours} 小时内没有发现告警")
        return

    print(f"📊 找到 {len(content)} 条告警记录 (总共 {total_elements} 条)")
    if customer_id:
        print(f"   客户过滤: {customer_id}")
    print()

    # 显示告警详情
    for alert in content:
        format_alert(alert)

    # 显示分页信息
    if total_pages > 1:
        print(f"📄 分页信息: 第1页，共{total_pages}页，每页{len(content)}条")

def fetch_alerts_by_customer_api(customer_id, hours=24):
    """通过API拉取特定客户的告警信息"""

    print(f"🔍 通过API拉取客户 {customer_id} 的最新告警信息")
    print("=" * 60)

    fetch_recent_alerts_api(hours=hours, customer_id=customer_id)

def test_api_connectivity():
    """测试API连接性"""

    client = AlertAPIClient()

    print("🔗 测试API连接性")
    print("=" * 30)

    # 测试获取统计信息
    analytics = client.get_alert_analytics()
    if analytics:
        print("✅ 统计API: 正常")
    else:
        print("❌ 统计API: 失败")

    # 测试获取告警列表
    alerts = client.get_alerts(page=0, size=1)
    if alerts and 'content' in alerts:
        print("✅ 告警列表API: 正常")
    else:
        print("❌ 告警列表API: 失败")

    print()

def main():
    parser = argparse.ArgumentParser(description='通过API拉取告警信息')
    parser.add_argument('--hours', type=int, default=24, help='查询过去多少小时的告警 (默认: 24)')
    parser.add_argument('--limit', type=int, default=50, help='最多显示多少条告警 (默认: 50)')
    parser.add_argument('--customer', type=str, help='指定客户ID，只显示该客户的告警')
    parser.add_argument('--test', action='store_true', help='测试API连接性')
    parser.add_argument('--url', type=str, default='http://localhost:8082', help='API基础URL (默认: http://localhost:8082)')

    args = parser.parse_args()

    # 设置API客户端URL
    global client
    client = AlertAPIClient(args.url)

    if args.test:
        test_api_connectivity()
        return

    if args.customer:
        fetch_alerts_by_customer_api(args.customer, args.hours)
    else:
        fetch_recent_alerts_api(args.hours, args.limit)

if __name__ == "__main__":
    main()