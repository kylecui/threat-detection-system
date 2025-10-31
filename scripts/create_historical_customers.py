#!/usr/bin/env python3
"""
创建历史客户数据脚本
为customer_a到customer_f创建客户记录
"""

import psycopg2
import random
from datetime import datetime, timezone

def generate_mock_email(customer_name):
    """根据客户名称生成mock邮箱"""
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

def create_historical_customers():
    """创建历史客户记录"""

    # 历史客户列表
    historical_customers = [
        ('customer_a', '客户A'),
        ('customer_b', '客户B'),
        ('customer_c', '客户C'),
        ('customer_d', '客户D'),
        ('customer_e', '客户E'),
        ('customer_f', '客户F')
    ]

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
        print("开始创建历史客户记录...")

        for customer_id, customer_name in historical_customers:
            # 生成mock数据
            email = generate_mock_email(customer_name)
            phone = generate_mock_phone()
            address = generate_mock_address(customer_name)
            status = 'ACTIVE'
            subscription_tier = random.choice(['FREE', 'BASIC', 'PROFESSIONAL', 'ENTERPRISE'])
            max_devices = random.randint(10, 100)
            description = f"{customer_name} - 历史客户 (2023-2024)"

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

            print(f"创建历史客户: {customer_id} - {customer_name}")

        # 提交事务
        conn.commit()
        print(f"\n成功创建了 {len(historical_customers)} 个历史客户记录")

        # 验证结果
        cursor.execute("SELECT COUNT(*) FROM customers")
        customer_count = cursor.fetchone()[0]

        print(f"验证结果: 总共 {customer_count} 个客户")

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
    print("开始创建历史客户数据...")
    create_historical_customers()
    print("脚本执行完成！")