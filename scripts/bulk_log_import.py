#!/usr/bin/env python3
"""
批量日志导入脚本 - 支持场景感知导入
用途: 将指定目录下的日志文件导入到系统中，支持不同的导入场景

支持的导入模式:
    migration: 系统迁移，合并历史数据和APT积累
    completion: 数据补全，针对特定客户的缺失数据
    offline: 离线分析，独立处理用于安全研究

使用方法:
    python bulk_log_import.py /path/to/logs/directory --mode migration [--customer-id CUST001]
    python bulk_log_import.py /path/to/logs/directory --mode completion --customer-id CUST001
    python bulk_log_import.py /path/to/logs/directory --mode offline

参数:
    directory: 日志文件目录路径
    --mode: 导入模式 (migration|completion|offline)
    --customer-id: 客户ID (completion模式时必需)
    --host: 数据摄取服务主机 (默认: localhost)
    --port: 数据摄取服务端口 (默认: 8080)
    --dry-run: 仅显示将要发送的日志，不实际发送
    --batch-size: 批量发送大小 (默认: 100)
    --delay: 发送批次间的延迟秒数 (默认: 1.0)
    --max-batches: 最大发送批次数 (可选，用于测试)

日志格式支持:
    1. 纯syslog格式: syslog_version=1.10.0,dev_serial=...,log_type=1,...
    2. JSON包装格式: {"message": "syslog_content"} 或 {"event": {"original": "syslog_content"}}
    3. 混合格式: 自动检测并提取syslog内容

示例:
    # 系统迁移导入
    python bulk_log_import.py tmp/production_test_logs/2025-09-18 --mode migration

    # 客户数据补全
    python bulk_log_import.py tmp/customer_logs/ --mode completion --customer-id customer-001

    # 离线分析导入
    python bulk_log_import.py tmp/research_logs/ --mode offline --dry-run

    # 指定服务地址
    python bulk_log_import.py tmp/logs/ --mode migration --host ingestion.example.com --port 8080
"""

import os
import sys
import json
import requests
import time
import argparse
from pathlib import Path
from typing import List, Tuple, Optional, Dict, Any


class ScenarioAwareImporter:
    """场景感知导入器"""

    def __init__(self, host: str = 'localhost', port: int = 8080, timeout: float = 30.0):
        self.base_url = f"http://{host}:{port}/api/v1/import"
        self.timeout = timeout

    def test_connection(self) -> bool:
        """测试服务连接"""
        try:
            response = requests.get(f"{self.base_url}/health", timeout=self.timeout)
            return response.status_code == 200
        except Exception:
            return False

    def import_logs(self, logs: List[str], mode: str, customer_id: Optional[str] = None) -> Dict[str, Any]:
        """导入日志批次"""
        url = self._get_import_url(mode, customer_id)

        payload = {
            "mode": mode,
            "logs": logs
        }

        if customer_id:
            payload["customerId"] = customer_id

        try:
            response = requests.post(url, json=payload, timeout=self.timeout)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            raise Exception(f"Import request failed: {e}")

    def _get_import_url(self, mode: str, customer_id: Optional[str]) -> str:
        """获取导入URL"""
        if mode == "migration":
            return f"{self.base_url}/migration"
        elif mode == "completion":
            if not customer_id:
                raise ValueError("Customer ID is required for completion mode")
            return f"{self.base_url}/completion/{customer_id}"
        elif mode == "offline":
            return f"{self.base_url}/offline"
        else:
            return f"{self.base_url}/scenario"

    def get_supported_modes(self) -> List[str]:
        """获取支持的导入模式"""
        try:
            response = requests.get(f"{self.base_url}/modes", timeout=self.timeout)
            response.raise_for_status()
            return response.json()
        except Exception:
            return ["migration", "completion", "offline"]  # 默认值


class LogstashImporter:
    """Logstash日志导入器 (向后兼容)"""

    def __init__(self, host: str = 'localhost', port: int = 9080, timeout: float = 5.0):
        self.host = host
        self.port = port
        self.timeout = timeout

    def test_connection(self) -> bool:
        """测试TCP连接"""
        import socket
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(self.timeout)
            # debug
            print(f"Testing connection to {self.host}:{self.port}")
            result = sock.connect_ex((self.host, self.port))
            # debug
            print(f"Connection test result: {result}")
            sock.close()
            return result == 0
        except Exception as e:
            print(f"连接测试失败: {e}")
            return False

    def send_batch(self, logs: List[str], batch_size: int = 100, delay: float = 1.0, max_batches: Optional[int] = None) -> Tuple[int, int]:
        """批量发送日志到Logstash (向后兼容)"""
        import socket
        total_sent = 0
        total_failed = 0

        for i in range(0, len(logs), batch_size):
            batch_num = i // batch_size + 1
            total_batches = (len(logs) + batch_size - 1) // batch_size

            # 检查是否达到最大批次数限制
            if max_batches and batch_num > max_batches:
                print(f"已达到最大批次数限制 ({max_batches})，停止发送")
                break

            batch = logs[i:i + batch_size]
            batch_sent = 0
            batch_failed = 0

            print(f"发送批次 {batch_num}/{total_batches} ({len(batch)} 条日志)")

            for log in batch:
                try:
                    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                    sock.settimeout(self.timeout)
                    sock.connect((self.host, self.port))
                    sock.sendall((log + '\n').encode('utf-8'))
                    sock.close()
                    batch_sent += 1
                except Exception as e:
                    print(f"发送失败: {e}", file=sys.stderr)
                    batch_failed += 1

            total_sent += batch_sent
            total_failed += batch_failed

            print(f"  批次结果: 成功={batch_sent}, 失败={batch_failed}")

            # 批次间延迟
            if i + batch_size < len(logs) and (not max_batches or batch_num < max_batches):
                time.sleep(delay)

        return total_sent, total_failed
    # """Logstash日志导入器"""

    # def __init__(self, host: str = 'localhost', port: int = 9080, timeout: float = 5.0):
    #     self.host = host
    #     self.port = port
    #     self.timeout = timeout

    # def test_connection(self) -> bool:
    #     """测试TCP连接"""
    #     try:
    #         sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    #         sock.settimeout(self.timeout)
    #         result = sock.connect_ex((self.host, self.port))
    #         sock.close()
    #         return result == 0
    #     except Exception:
    #         return False

    def send_log(self, log_content: str) -> bool:
        """发送单条日志"""
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(self.timeout)
            sock.connect((self.host, self.port))
            sock.sendall((log_content + '\n').encode('utf-8'))
            sock.close()
            return True
        except Exception as e:
            print(f"发送失败: {e}", file=sys.stderr)
            return False

    # def send_batch(self, logs: List[str], batch_size: int = 100, delay: float = 1.0, max_batches: Optional[int] = None) -> Tuple[int, int]:
    #     """批量发送日志"""
    #     total_sent = 0
    #     total_failed = 0

    #     for i in range(0, len(logs), batch_size):
    #         batch_num = i // batch_size + 1
    #         total_batches = (len(logs) + batch_size - 1) // batch_size
            
    #         # 检查是否达到最大批次数限制
    #         if max_batches and batch_num > max_batches:
    #             print(f"已达到最大批次数限制 ({max_batches})，停止发送")
    #             break
                
    #         batch = logs[i:i + batch_size]
    #         batch_sent = 0
    #         batch_failed = 0

    #         print(f"发送批次 {batch_num}/{total_batches} ({len(batch)} 条日志)")

    #         for log in batch:
    #             if self.send_log(log):
    #                 batch_sent += 1
    #             else:
    #                 batch_failed += 1

    #         total_sent += batch_sent
    #         total_failed += batch_failed

    #         print(f"  批次结果: 成功={batch_sent}, 失败={batch_failed}")

    #         # 批次间延迟
    #         if i + batch_size < len(logs) and (not max_batches or batch_num < max_batches):
    #             time.sleep(delay)

    #     return total_sent, total_failed


class LogParser:
    """日志解析器"""

    @staticmethod
    def extract_syslog_from_json(json_line: str) -> Optional[str]:
        """从JSON格式的日志中提取syslog内容"""
        try:
            log_entry = json.loads(json_line.strip())

            # 优先检查event.original字段 (ELK格式)
            if 'event' in log_entry and 'original' in log_entry['event']:
                original = log_entry['event']['original']
                if LogParser.is_valid_syslog(original):
                    return original.strip()

            # 检查message字段
            if 'message' in log_entry:
                message = log_entry['message']
                if LogParser.is_valid_syslog(message):
                    return message.strip()

            # 检查log.syslog.original字段
            if 'log' in log_entry and 'syslog' in log_entry['log'] and 'original' in log_entry['log']['syslog']:
                syslog_original = log_entry['log']['syslog']['original']
                if LogParser.is_valid_syslog(syslog_original):
                    return syslog_original.strip()

            return None

        except json.JSONDecodeError:
            return None

    @staticmethod
    def is_valid_syslog(content: str) -> bool:
        """检查是否为有效的syslog内容"""
        if not content or not isinstance(content, str):
            return False

        # 检查是否包含必需的syslog字段
        required_patterns = ['syslog_version=', 'dev_serial=', 'log_type=']
        return all(pattern in content for pattern in required_patterns)

    @staticmethod
    def parse_log_file(file_path: Path) -> List[str]:
        """解析单个日志文件"""
        syslog_logs = []

        try:
            with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                for line_num, line in enumerate(f, 1):
                    line = line.strip()
                    if not line:
                        continue

                    # 尝试提取syslog内容
                    syslog_content = LogParser.extract_syslog_from_json(line)

                    if syslog_content:
                        syslog_logs.append(syslog_content)
                    elif LogParser.is_valid_syslog(line):
                        # 直接是syslog格式
                        syslog_logs.append(line)
                    else:
                        print(f"警告: {file_path}:{line_num} - 无法提取有效的syslog内容", file=sys.stderr)

        except Exception as e:
            print(f"错误: 读取文件 {file_path} 失败: {e}", file=sys.stderr)

        return syslog_logs


def main():
    parser = argparse.ArgumentParser(
        description="批量日志导入脚本 - 支持场景感知导入",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )

    parser.add_argument('directory', help='日志文件目录路径')
    parser.add_argument('--mode', choices=['migration', 'completion', 'offline'],
                       default='migration', help='导入模式 (默认: migration)')
    parser.add_argument('--customer-id', help='客户ID (completion模式时必需)')
    parser.add_argument('--host', default='localhost', help='服务主机 (默认: localhost)')
    parser.add_argument('--port', type=int, default=None, help='服务端口 (默认: 8080, 或在 --legacy-logstash 模式下为 9080)')
    parser.add_argument('--dry-run', action='store_true', help='仅显示将要发送的日志，不实际发送')
    parser.add_argument('--batch-size', type=int, default=100, help='批量发送大小 (默认: 100)')
    parser.add_argument('--delay', type=float, default=1.0, help='发送批次间的延迟秒数 (默认: 1.0)')
    parser.add_argument('--timeout', type=float, default=30.0, help='HTTP请求超时秒数 (默认: 30.0)')
    parser.add_argument('--max-batches', type=int, help='最大发送批次数 (可选，用于测试)')
    parser.add_argument('--legacy-logstash', action='store_true',
                       help='使用传统Logstash TCP模式 (向后兼容), 默认端口为9080')

    args = parser.parse_args()

    # 验证参数
    if args.mode == 'completion' and not args.customer_id:
        print("错误: completion模式必须指定--customer-id", file=sys.stderr)
        sys.exit(1)

    # 检查目录是否存在
    log_dir = Path(args.directory)
    if not log_dir.exists() or not log_dir.is_dir():
        print(f"错误: 目录不存在或不是目录: {log_dir}", file=sys.stderr)
        sys.exit(1)

    # 查找所有.log文件
    log_files = list(log_dir.glob('*.log'))
    if not log_files:
        print(f"错误: 在目录 {log_dir} 中未找到任何 .log 文件", file=sys.stderr)
        sys.exit(1)

    # 添加处理默认值的逻辑
    # 检查用户是否 *没有* 指定 --port
    if args.port is None:
        if args.legacy_logstash:
            # 如果开启了 legacy 模式且用户未指定端口，则设置为 9080
            args.port = 9080
        else:
            # 如果关闭了 legacy 模式且用户未指定端口，则设置为 8080
            args.port = 8080

    print(f"找到 {len(log_files)} 个日志文件:")
    for log_file in log_files:
        print(f"  - {log_file.name}")

    # 解析所有日志文件
    all_logs = []
    total_files = len(log_files)
    total_logs = 0

    print(f"\n解析日志文件...")
    for i, log_file in enumerate(log_files, 1):
        print(f"处理文件 {i}/{total_files}: {log_file.name}")
        logs = LogParser.parse_log_file(log_file)
        all_logs.extend(logs)
        total_logs += len(logs)
        print(f"  提取到 {len(logs)} 条有效日志")

        # 检查是否达到最大日志数量限制
        if args.max_batches and len(all_logs) >= args.max_batches * args.batch_size:
            print(f"已达到最大日志数量限制 ({args.max_batches * args.batch_size})，停止解析更多文件")
            break

    if not all_logs:
        print("错误: 未找到任何有效的syslog日志", file=sys.stderr)
        sys.exit(1)

    print(f"\n解析完成:")
    print(f"  - 处理文件数: {total_files}")
    print(f"  - 总日志数: {total_logs}")

    # 显示日志类型统计
    attack_logs = [log for log in all_logs if 'log_type=1' in log]
    heartbeat_logs = [log for log in all_logs if 'log_type=2' in log]
    other_logs = [log for log in all_logs if 'log_type=1' not in log and 'log_type=2' not in log]

    print(f"  - 攻击日志: {len(attack_logs)}")
    print(f"  - 心跳日志: {len(heartbeat_logs)}")
    print(f"  - 其他日志: {len(other_logs)}")

    # 干运行模式
    if args.dry_run:
        print(f"\n干运行模式 - 将以'{args.mode}'模式发送 {len(all_logs)} 条日志到 {args.host}:{args.port}")
        if args.customer_id:
            print(f"客户ID: {args.customer_id}")

        # 计算批次信息
        total_batches = (len(all_logs) + args.batch_size - 1) // args.batch_size
        max_display_batches = args.max_batches if args.max_batches else total_batches

        print(f"批次大小: {args.batch_size}, 总批次数: {total_batches}")
        if args.max_batches:
            print(f"限制批次数: {args.max_batches}")

        print("\n批次预览:")
        for i in range(min(max_display_batches, total_batches)):
            start_idx = i * args.batch_size
            end_idx = min(start_idx + args.batch_size, len(all_logs))
            batch_logs = all_logs[start_idx:end_idx]
            print(f"批次 {i+1}: {len(batch_logs)} 条日志")

            # 只显示第一批的前几条日志作为示例
            if i == 0:
                for j, log in enumerate(batch_logs[:3], 1):
                    print(f"  {j}. {log[:100]}{'...' if len(log) > 100 else ''}")
                if len(batch_logs) > 3:
                    print(f"  ... 还有 {len(batch_logs) - 3} 条日志")

        return

    # 选择导入器
    if args.legacy_logstash:
        print(f"\n使用传统Logstash TCP模式")
        # logstash_server_port = args.port if args.port else 9080
        importer = LogstashImporter(args.host, args.port, args.timeout)
        target_desc = f"Logstash {args.host}:{args.port}"
    else:
        print(f"\n使用场景感知导入模式: {args.mode}")
        importer = ScenarioAwareImporter(args.host, args.port, args.timeout)
        target_desc = f"Data Ingestion Service {args.host}:{args.port}"

    # 测试连接
    print(f"测试连接: {target_desc}")
    if not importer.test_connection():
        print(f"错误: 无法连接到 {target_desc}", file=sys.stderr)
        if not args.legacy_logstash:
            print("请确保数据摄取服务正在运行且端口可访问", file=sys.stderr)
        else:
            print("请确保Logstash正在运行且TCP端口可访问", file=sys.stderr)
        sys.exit(1)

    print("连接成功 ✓")

    # 发送日志
    print(f"\n开始导入日志...")
    print(f"模式: {args.mode}")
    if args.customer_id:
        print(f"客户ID: {args.customer_id}")
    print(f"目标: {target_desc}")
    print(f"批量大小: {args.batch_size}")
    print(f"批次延迟: {args.delay}秒")
    if args.max_batches:
        print(f"最大批次数: {args.max_batches}")

    start_time = time.time()

    if args.legacy_logstash:
        # 传统Logstash模式
        sent, failed = importer.send_batch(all_logs, args.batch_size, args.delay, args.max_batches)
        total_sent = sent
        total_failed = failed
    else:
        # 场景感知导入模式
        total_sent = 0
        total_failed = 0

        for i in range(0, len(all_logs), args.batch_size):
            batch_num = i // args.batch_size + 1
            total_batches = (len(all_logs) + args.batch_size - 1) // args.batch_size

            # 检查是否达到最大批次数限制
            if args.max_batches and batch_num > args.max_batches:
                print(f"已达到最大批次数限制 ({args.max_batches})，停止发送")
                break

            batch = all_logs[i:i + args.batch_size]
            print(f"发送批次 {batch_num}/{total_batches} ({len(batch)} 条日志)")

            try:
                result = importer.import_logs(batch, args.mode, args.customer_id)
                batch_sent = result.get('processedLogs', 0)
                batch_failed = result.get('errorLogs', 0)

                total_sent += batch_sent
                total_failed += batch_failed

                print(f"  批次结果: 成功={batch_sent}, 失败={batch_failed}")
                if 'duplicateLogs' in result and result['duplicateLogs'] > 0:
                    print(f"  去重: {result['duplicateLogs']} 条重复日志")

            except Exception as e:
                print(f"  批次失败: {e}", file=sys.stderr)
                total_failed += len(batch)

            # 批次间延迟
            if i + args.batch_size < len(all_logs) and (not args.max_batches or batch_num < args.max_batches):
                time.sleep(args.delay)

    end_time = time.time()

    # 显示结果
    print(f"\n导入完成!")
    print(f"总日志数: {len(all_logs)}")
    print(f"成功导入: {total_sent}")
    print(f"导入失败: {total_failed}")
    print(".2f")
    print(".2f")

    if total_failed > 0:
        print(f"\n警告: {total_failed} 条日志导入失败", file=sys.stderr)
        sys.exit(1)
    else:
        print("\n所有日志导入成功 ✓")


if __name__ == "__main__":
    main()