# 🚀 快速参考卡片

## 最常用命令 (Copy & Paste)

### 🔄 完整重启 (代码修改后必做!)

```bash
# 方式1: 使用自动化脚本 (推荐)
./scripts/tools/full_restart.sh

# 方式2: 手动执行
mvn clean package -DskipTests && \
cd docker && \
docker compose build --no-cache && \
docker compose up -d && \
cd ..
```

### 🧪 运行E2E测试

```bash
python3 scripts/test/e2e_mvp_test.py
```

### 📊 检查数据持久化

```bash
# 使用自动化脚本
./scripts/tools/check_persistence.sh

# 手动查询
docker exec postgres psql -U threat_user -d threat_detection -c \
  "SELECT COUNT(*) FROM attack_events WHERE created_at > NOW() - INTERVAL '10 minutes';"
```

### 📝 查看日志

```bash
# 实时查看所有核心服务
./scripts/tools/tail_logs.sh

# 查看单个服务
docker logs -f data-ingestion-service

# 查看最近100行
docker logs data-ingestion-service --tail 100

# 搜索错误
docker logs data-ingestion-service 2>&1 | grep -i "error\|exception"
```

### 🐛 快速诊断

```bash
# 检查所有服务状态
cd docker && docker compose ps

# 检查Kafka消费者
docker exec kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --list

# 检查数据库连接
docker exec postgres psql -U threat_user -d threat_detection -c "\dt"

# 查看持久化成功日志
docker logs data-ingestion-service | grep "Persisted"
```

---

## ⚠️ 关键提醒

| 场景 | 必须做的事 |
|------|-----------|
| **修改Java代码** | `mvn clean package` → `docker compose build --no-cache` → `docker compose up -d` |
| **修改配置文件** | 同上 |
| **修改SQL** | 直接在数据库执行,无需重启 |
| **修改docker-compose.yml** | `docker compose up -d` (无需rebuild) |

---

## 📁 重要文件路径

```
测试脚本:     scripts/test/e2e_mvp_test.py
测试数据:     tmp/real_test_logs/*.log
工具脚本:     scripts/tools/*.sh
数据库SQL:    docker/*.sql
配置文件:     services/*/src/main/resources/application*.yml
Docker编排:   docker/docker-compose.yml
```

---

## 🔧 常用SQL查询

```sql
-- 最近10分钟的攻击事件
SELECT COUNT(*) as total,
       COUNT(DISTINCT attack_mac) as attackers,
       COUNT(DISTINCT response_port) as ports
FROM attack_events 
WHERE created_at > NOW() - INTERVAL '10 minutes';

-- 威胁等级分布
SELECT threat_level, tier, COUNT(*) 
FROM threat_alerts 
WHERE created_at > NOW() - INTERVAL '1 hour'
GROUP BY threat_level, tier;

-- 查看JSONB数据
SELECT attack_mac, raw_log_data 
FROM attack_events 
ORDER BY created_at DESC 
LIMIT 3;

-- 清空测试数据
TRUNCATE TABLE attack_events CASCADE;
TRUNCATE TABLE threat_alerts CASCADE;
```

---

## 🎯 调试流程

1. **修改代码** → 2. **Maven编译** → 3. **Docker构建** → 4. **容器重启** → 5. **查看日志** → 6. **验证功能**

**记住**: Docker容器不会自动加载新代码! 必须重新构建镜像!
