#!/usr/bin/env python3
"""
设备客户映射数据迁移脚本
1. 将现有13个序列号的有效期迁移至2023-01-01~2024-12-31
2. 导入客户和设备对应关系.xlsx，并设置有效期自2025-01-01开始
"""

import pandas as pd
import requests
from datetime import datetime, timezone
import json

def migrate_existing_devices():
    """将现有13个序列号的有效期迁移至2023-01-01~2024-12-31"""

    base_url = "http://localhost:8083/api/v1/devices"

    # 排除TEST_DEVICE_001，获取现有的13个设备
    existing_devices = [
        "10221e5a3be0cf2d", "44056bfd85030e0e", "5355ac453fe4e74d",
        "578b8eed4856244d", "6360b776893dc0cc", "GSFB2204200410007425",
        "a1fce03baf456aba", "a458749d86a13bde", "bce9288a4caa2c61",
        "c606765df087c8a6", "caa0beea29676c6d", "df01185343413132381002b2aaf96900",
        "eebe4c42df504ea5"
    ]

    print("🔄 迁移现有设备有效期至2023-01-01~2024-12-31")

    for device in existing_devices:
        try:
            # 首先查询当前映射
            response = requests.get(f"{base_url}/customer", params={
                'deviceSerial': device
            })

            if response.status_code == 200:
                result = response.json()
                current_customer = result.get('customerId')

                if current_customer:
                    # 解绑当前映射（设置结束时间为2024-12-31）
                    unbind_response = requests.post(f"{base_url}/unbind", params={
                        'deviceSerial': device,
                        'unbindReason': '迁移至2023-2024时间段',
                        'unbindTime': '2024-12-31T23:59:59Z'
                    })

                    # 重新绑定到2023-01-01开始
                    bind_response = requests.post(f"{base_url}/bind", params={
                        'deviceSerial': device,
                        'customerId': current_customer,
                        'bindReason': '历史数据迁移',
                        'bindTime': '2023-01-01T00:00:00Z'
                    })

                    if bind_response.status_code == 200:
                        print(f"✅ {device} -> {current_customer} (2023-01-01 ~ 2024-12-31)")
                    else:
                        print(f"❌ {device} 重新绑定失败: {bind_response.status_code}")
                else:
                    print(f"⚠️  {device} 未找到当前客户映射")
            else:
                print(f"❌ {device} 查询失败: {response.status_code}")

        except Exception as e:
            print(f"❌ {device} 处理失败: {e}")

def import_excel_devices():
    """导入Excel文件中的客户设备对应关系"""

    excel_file = "/home/kylecui/threat-detection-system/tmp/客户和设备对应关系.xlsx"

    try:
        # 读取Excel文件
        df = pd.read_excel(excel_file)
        print(f"📊 读取到 {len(df)} 条记录")

        # 显示前几行数据结构
        print("📋 数据结构预览:")
        print(df.head())

        base_url = "http://localhost:8083/api/v1/devices"

        print("\n🔄 开始导入Excel数据 (有效期: 2025-01-01 ~ 持续有效)")

        success_count = 0
        for index, row in df.iterrows():
            try:
                device_serial = str(row['设备名称']).strip()
                customer_id_num = int(row['客户ID'])
                customer_id = f"customer_{customer_id_num}"  # 转换为customer_前缀格式

                print(f"处理设备: {device_serial} -> {customer_id}")

                # 检查是否已存在当前映射
                check_response = requests.get(f"{base_url}/customer", params={
                    'deviceSerial': device_serial
                })

                if check_response.status_code == 200:
                    result = check_response.json()
                    if result.get('found'):
                        # 如果存在当前映射，先解绑
                        unbind_response = requests.post(f"{base_url}/unbind", params={
                            'deviceSerial': device_serial,
                            'unbindReason': '导入新客户关系',
                            'unbindTime': '2024-12-31T23:59:59Z'
                        })
                        print(f"  解绑旧映射: {unbind_response.status_code}")

                # 绑定到新客户 (2025-01-01开始)
                bind_response = requests.post(f"{base_url}/bind", params={
                    'deviceSerial': device_serial,
                    'customerId': customer_id,
                    'bindReason': 'Excel数据导入',
                    'bindTime': '2025-01-01T00:00:00Z'
                })

                if bind_response.status_code == 200:
                    print(f"✅ {device_serial} -> {customer_id}")
                    success_count += 1
                else:
                    print(f"❌ {device_serial} -> {customer_id} 失败: {bind_response.status_code}")
                    print(f"   响应: {bind_response.text}")

            except Exception as e:
                print(f"❌ 处理第{index+1}行失败: {e}")

        print(f"\n🎉 导入完成! 成功导入 {success_count}/{len(df)} 条记录")

    except Exception as e:
        print(f"❌ 读取Excel文件失败: {e}")
        import traceback
        traceback.print_exc()

def verify_migration():
    """验证迁移结果"""

    base_url = "http://localhost:8083/api/v1/devices"

    print("\n🔍 验证迁移结果")

    # 检查活跃映射
    response = requests.get(f"{base_url}/active")
    if response.status_code == 200:
        active = response.json()
        print(f"📊 当前活跃映射数量: {len(active)}")

        # 按客户分组统计
        customer_stats = {}
        for mapping in active:
            customer = mapping.get('customerId', 'unknown')
            customer_stats[customer] = customer_stats.get(customer, 0) + 1

        print("👥 客户设备分布:")
        for customer, count in sorted(customer_stats.items()):
            print(f"  {customer}: {count} 台设备")
    else:
        print(f"❌ 获取活跃映射失败: {response.status_code}")

def main():
    print("🚀 开始设备客户映射数据迁移")
    print("=" * 50)

    # 步骤1: 迁移现有设备
    migrate_existing_devices()

    # 步骤2: 导入Excel数据
    import_excel_devices()

    # 步骤3: 验证结果
    verify_migration()

    print("\n✅ 数据迁移完成!")

if __name__ == "__main__":
    main()