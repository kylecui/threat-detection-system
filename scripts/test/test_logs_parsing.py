#!/usr/bin/env python3
"""
测试脚本：使用tmp/test_logs目录中的样例日志测试data-ingestion服务
"""

import os
import json
import requests
import time
from pathlib import Path

# API配置
API_BASE_URL = "http://localhost:8080"
BATCH_ENDPOINT = f"{API_BASE_URL}/api/v1/logs/batch"

def extract_syslog_from_json(json_line):
    """从JSON格式的日志中提取syslog内容"""
    try:
        log_entry = json.loads(json_line.strip())

        # 优先检查event.original字段
        if 'event' in log_entry and 'original' in log_entry['event']:
            original = log_entry['event']['original']
            # 如果是标准的syslog格式（以<开头）或包含syslog_version=
            if original.startswith('<') or 'syslog_version=' in original:
                return original.strip()

        # 检查message字段
        if 'message' in log_entry:
            message = log_entry['message']
            # 如果message包含syslog_version，说明是有效的syslog
            if 'syslog_version=' in message:
                return message.strip()

        # 检查log.syslog.original字段
        if 'log' in log_entry and 'syslog' in log_entry['log'] and 'original' in log_entry['log']['syslog']:
            syslog_original = log_entry['log']['syslog']['original']
            if 'syslog_version=' in syslog_original:
                return syslog_original.strip()

        # 如果都没有找到有效的syslog内容，返回None
        return None

    except json.JSONDecodeError:
        return None

def test_log_parsing(log_content, log_id):
    """测试单个日志的解析"""
    try:
        response = requests.post(
            f"{API_BASE_URL}/api/v1/logs/ingest",
            data=log_content,
            headers={'Content-Type': 'text/plain'},
            timeout=10
        )
        return {
            'log_id': log_id,
            'status_code': response.status_code,
            'response': response.text,
            'success': response.status_code == 200
        }
    except requests.exceptions.RequestException as e:
        return {
            'log_id': log_id,
            'error': str(e),
            'success': False
        }

def test_batch_parsing(logs):
    """测试批量日志解析"""
    try:
        batch_request = {'logs': logs}
        response = requests.post(
            BATCH_ENDPOINT,
            json=batch_request,
            headers={'Content-Type': 'application/json'},
            timeout=30
        )
        return {
            'status_code': response.status_code,
            'response': response.json() if response.status_code < 400 else response.text,
            'success': response.status_code == 200
        }
    except requests.exceptions.RequestException as e:
        return {
            'error': str(e),
            'success': False
        }

def main():
    """主函数"""
    test_logs_dir = Path("/home/kylecui/threat-detection-system/tmp/test_logs")

    if not test_logs_dir.exists():
        print(f"测试日志目录不存在: {test_logs_dir}")
        return

    # 等待服务启动
    print("等待服务启动...")
    time.sleep(10)

    # 检查服务是否可用
    try:
        response = requests.get(f"{API_BASE_URL}/api/v1/logs/health", timeout=5)
        if response.status_code != 200:
            print("服务不可用")
            return
    except:
        print("无法连接到服务")
        return

    print("开始测试日志解析...")

    all_logs = []
    failed_logs = []
    successful_logs = []

    # 处理每个日志文件
    for log_file in sorted(test_logs_dir.glob("*.log")):
        print(f"\n处理文件: {log_file.name}")

        with open(log_file, 'r', encoding='utf-8', errors='ignore') as f:
            for line_num, line in enumerate(f, 1):
                line = line.strip()
                if not line:
                    continue

                # 提取syslog内容
                syslog_content = extract_syslog_from_json(line)
                if syslog_content:
                    log_id = f"{log_file.name}:{line_num}"
                    all_logs.append((log_id, syslog_content))

                    # 测试单个日志解析
                    result = test_log_parsing(syslog_content, log_id)

                    if result['success']:
                        successful_logs.append((log_id, syslog_content))
                        print(f"✓ {log_id}: 解析成功")
                    else:
                        failed_logs.append((log_id, syslog_content, result))
                        print(f"✗ {log_id}: 解析失败 - {result.get('status_code', 'ERROR')}")

                    # 避免请求过于频繁
                    time.sleep(0.1)
                else:
                    # 这是一个无法提取syslog内容的JSON日志
                    log_id = f"{log_file.name}:{line_num}"
                    failed_logs.append((log_id, line, {'error': '无法提取syslog内容'}))
                    print(f"✗ {log_id}: 无法提取syslog内容")

    print("\n=== 测试结果汇总 ===")
    print(f"总日志数: {len(all_logs) + len([f for f in failed_logs if '无法提取syslog内容' in str(f[2])])}")
    print(f"有效syslog日志数: {len(all_logs)}")
    print(f"解析成功: {len(successful_logs)}")
    print(f"解析失败: {len(failed_logs)}")

    if failed_logs:
        print("\n=== 解析失败的日志 ===")
        for log_id, content, result in failed_logs:
            print(f"\n日志ID: {log_id}")
            print(f"内容: {content[:200]}{'...' if len(content) > 200 else ''}")
            if 'status_code' in result:
                print(f"状态码: {result['status_code']}")
                print(f"响应: {result.get('response', 'N/A')}")
            else:
                print(f"错误: {result.get('error', '未知错误')}")

    # 测试批量处理（只测试前10个成功的日志）
    if successful_logs:
        print("\n=== 测试批量处理 ===")
        batch_logs = [content for _, content in successful_logs[:10]]
        batch_result = test_batch_parsing(batch_logs)

        if batch_result['success']:
            print("✓ 批量处理成功")
            response_data = batch_result['response']
            print(f"总数: {response_data.get('totalCount', 'N/A')}")
            print(f"成功数: {response_data.get('successCount', 'N/A')}")
            print(f"失败数: {response_data.get('errorCount', 'N/A')}")
        else:
            print(f"✗ 批量处理失败: {batch_result.get('error', '未知错误')}")

if __name__ == "__main__":
    main()