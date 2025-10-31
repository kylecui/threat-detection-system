#!/usr/bin/env python3
"""
创建临时客户数据脚本
基于 tmp/客户和设备对应关系.xlsx 文件创建客户和设备映射关系
使用时间版本的device_customer_mapping表
"""

import pandas as pd
import psycopg2
import random
import string
from datetime import datetime, timezone

def generate_mock_email(customer_name):
    """根据客户名称生成mock邮箱"""
    # 移除特殊字符，转换为小写
    clean_name = ''.join(c for c in customer_name if c.isalnum() or c in ' ').lower()
    clean_name = clean_name.replace(' ', '.')
    domains = ['example.com', 'company.com', 'test.com', 'demo.com']
    domain = random.choice(domains)
    return f"{clean_name}@{domain}"

def generate_mock_phone():
    """生成mock电话号码"""
    return f"1{random.randint(3000000000, 9999999999)}"

def generate_mock_address(customer_name):
    """生成mock地址"""
    cities = ['北京市', '上海市', '深圳市', '广州市', '杭州市', '南京市', '武汉市', '成都市']
    city = random.choice(cities)
    return f"{city}{customer_name}公司"

def create_customers_and_mappings():
    """创建客户和设备映射关系"""

    # 读取Excel文件
    try:
        df = pd.read_excel('tmp/客户和设备对应关系.xlsx')
        print(f"成功读取Excel文件，包含 {len(df)} 条记录")
    except Exception as e:
        print(f"读取Excel文件失败: {e}")
        return

    # 数据库连接参数
    db_config = {
        'host': 'localhost',
        'port': 5432,
        'database': 'threat_detection',
        'user': 'threat_user',
        'password': 'threat_password'
    }

    try:
        # 连接数据库
        conn = psycopg2.connect(**db_config)
        cursor = conn.cursor()

        print("成功连接数据库")

        # 用于跟踪已处理的客户，避免重复插入
        processed_customers = set()

        for index, row in df.iterrows():
            customer_id_num = row['客户ID']
            customer_name = row['客户名称']
            device_id = row['设备ID']
            device_serial = row['设备名称']

            # 生成customer_id
            customer_id = f"customer_{customer_id_num}"

            # 如果客户还未处理，创建客户记录
            if customer_id not in processed_customers:
                # 生成mock数据
                email = generate_mock_email(customer_name)
                phone = generate_mock_phone()
                address = generate_mock_address(customer_name)
                status = 'ACTIVE'
                subscription_tier = random.choice(['FREE', 'BASIC', 'PROFESSIONAL', 'ENTERPRISE'])
                max_devices = random.randint(10, 100)
                description = f"{customer_name} - 临时测试客户"

                # 插入客户记录
                insert_customer_sql = """
                INSERT INTO customers (
                    customer_id, name, email, phone, address, status,
                    subscription_tier, max_devices, description,
                    created_at, updated_at
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (customer_id) DO NOTHING
                """

                now = datetime.now(timezone.utc)

                cursor.execute(insert_customer_sql, (
                    customer_id, customer_name, email, phone, address, status,
                    subscription_tier, max_devices, description, now, now
                ))

                processed_customers.add(customer_id)
                print(f"创建客户: {customer_id} - {customer_name}")

            # 创建设备映射关系 (时间版本)
            # 首先检查是否已有活跃映射，如果有则先解绑
            check_existing_sql = """
            SELECT id FROM device_customer_mapping
            WHERE dev_serial = %s AND unbind_time IS NULL
            """

            cursor.execute(check_existing_sql, (device_serial,))
            existing = cursor.fetchone()

            if existing:
                # 解绑现有映射
                unbind_sql = """
                UPDATE device_customer_mapping
                SET unbind_time = %s, updated_at = %s
                WHERE id = %s
                """
                now = datetime.now(timezone.utc)
                cursor.execute(unbind_sql, (now, now, existing[0]))
                print(f"解绑现有映射: {device_serial}")

            # 创建新映射 (2025-01-01开始，持续有效)
            insert_mapping_sql = """
            INSERT INTO device_customer_mapping (
                dev_serial, customer_id, bind_time, description, created_at, updated_at
            ) VALUES (%s, %s, %s, %s, %s, %s)
            """

            bind_time = datetime(2025, 1, 1, tzinfo=timezone.utc)  # 2025年1月1日开始
            now = datetime.now(timezone.utc)
            description = f"设备 {device_serial} 属于客户 {customer_name} (2025+配置)"

            cursor.execute(insert_mapping_sql, (
                device_serial, customer_id, bind_time, description, now, now
            ))
            print(f"创建设备映射: {device_serial} -> {customer_id}")

        # 提交事务
        conn.commit()
        print(f"\n成功处理完成！共创建了 {len(processed_customers)} 个客户和 {len(df)} 个设备映射关系")

        # 验证结果
        cursor.execute("SELECT COUNT(*) FROM customers")
        customer_count = cursor.fetchone()[0]

        cursor.execute("SELECT COUNT(*) FROM device_customer_mapping WHERE unbind_time IS NULL")
        mapping_count = cursor.fetchone()[0]

        print(f"验证结果: {customer_count} 个客户, {mapping_count} 个活跃设备映射")

    except Exception as e:
        print(f"数据库操作失败: {e}")
        if 'conn' in locals():
            conn.rollback()
        raise
    finally:
        if 'cursor' in locals():
            cursor.close()
        if 'conn' in locals():
            conn.close()

if __name__ == "__main__":
    print("开始创建临时客户数据...")
    create_customers_and_mappings()
    print("脚本执行完成！")