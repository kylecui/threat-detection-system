# 容器重建流程 - 快速参考

**强制执行**: 所有代码修改后必须遵循此流程 ⚠️

---

## 🚀 方法一：使用快速脚本（推荐）

```bash
cd ~/threat-detection-system/scripts
./rebuild_service.sh <service-name>
```

**示例**:
```bash
./rebuild_service.sh threat-assessment
./rebuild_service.sh data-ingestion
./rebuild_service.sh alert-management
```

---

## 📋 方法二：手动执行（标准流程）

### 第一步：重新编译
```bash
cd ~/threat-detection-system/services/<service-name>
mvn clean package -DskipTests
```

### 第二步：重构容器
```bash
cd ~/threat-detection-system/docker
docker compose down -v <service-name>
docker compose build --no-cache <service-name>
docker compose up -d --force-recreate <service-name>
```

### 第三步：检查容器状态
```bash
docker compose ps
docker logs <service-name>-service --tail 30
```

---

## ✅ 完整示例（threat-assessment）

```bash
# 1. 编译
cd ~/threat-detection-system/services/threat-assessment
mvn clean package -DskipTests

# 2. 重构容器
cd ~/threat-detection-system/docker
docker compose down -v threat-assessment
docker compose build --no-cache threat-assessment
docker compose up -d --force-recreate threat-assessment

# 3. 验证
docker compose ps
docker logs threat-assessment-service --tail 30

# 4. 测试
cd ~/threat-detection-system/scripts
bash test_v4_phase1_integration.sh
```

---

## ⚠️ 关键参数说明

| 参数 | 作用 | 为什么必须 |
|------|------|-----------|
| `mvn clean` | 清理旧编译产物 | 避免混用新旧代码 |
| `-DskipTests` | 跳过单元测试 | 加快编译速度 |
| `down -v` | 删除容器和数据卷 | 清理旧数据 |
| `--no-cache` | 不使用 Docker 缓存 | 确保代码完全生效 |
| `--force-recreate` | 强制重建容器 | 确保使用新镜像 |

---

## 🚫 禁止操作

- ❌ `docker compose restart` （只重启，不加载新代码）
- ❌ 跳过 `mvn clean package`
- ❌ 省略 `--no-cache`
- ❌ 忘记验证容器状态

---

**详细文档**: `docs/guides/CONTAINER_REBUILD_WORKFLOW.md`  
**快速脚本**: `scripts/rebuild_service.sh`
