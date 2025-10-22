# 构建系统验证计划

**日期**: 2025-10-22  
**目标**: 验证新的 Docker 构建优化系统的功能性和可靠性  
**预计时间**: 15-20 分钟

---

## ✅ 验证检查清单

### 阶段 1: 环境验证 (2 分钟)
- [x] 脚本权限: `-rwxr-xr-x` ✅
- [x] Docker 安装: v27.5.1 ✅
- [x] Docker Compose 安装: v1.29.2 ✅
- [x] 项目结构完整 ✅
- [x] Alert Management 服务存在 ✅

### 阶段 2: Maven 编译验证 (5 分钟)
- [ ] `mvn clean compile` 成功
- [ ] 无编译错误
- [ ] JAR 文件生成在 `target/` 中
- [ ] Build timestamp 更新

### 阶段 3: Docker 构建验证 (7 分钟)
- [ ] 快速构建执行: `bash scripts/rebuild.sh alert-management`
- [ ] 新的 Docker 镜像生成
- [ ] 镜像大小合理 (~380 MB)
- [ ] BUILD_DATE 参数传入正确
- [ ] cache_from: [] 生效 (每次都重新构建)

### 阶段 4: 容器运行验证 (3 分钟)
- [ ] 容器启动成功
- [ ] 健康检查通过
- [ ] 日志输出正常 (WARN 级别)
- [ ] 无启动错误

### 阶段 5: 配置变更验证 (2 分钟)
- [ ] 修改环境变量测试
- [ ] 重建后生效验证
- [ ] 缓存问题已解决

### 阶段 6: 性能指标 (1 分钟)
- [ ] 快速重建时间: < 5 分钟
- [ ] 完整重建时间: < 10 分钟
- [ ] 资源使用合理

---

## 🎯 验证步骤

### 1️⃣  Maven 编译阶段
```bash
cd /home/kylecui/threat-detection-system/services/alert-management
mvn clean compile -DskipTests
```

**预期结果**:
- ✅ `BUILD SUCCESS`
- ✅ JAR 生成在 `target/alert-management-1.0.0-SNAPSHOT.jar`

### 2️⃣  快速重建测试
```bash
bash scripts/rebuild.sh alert-management
```

**预期结果**:
- ✅ 脚本执行成功
- ✅ 时间: 2-5 分钟
- ✅ 新镜像生成
- ✅ 容器启动健康

### 3️⃣  完整重建测试
```bash
bash scripts/rebuild.sh alert-management full
```

**预期结果**:
- ✅ 脚本执行成功
- ✅ 时间: 5-10 分钟
- ✅ 系统缓存清理
- ✅ 全新镜像构建

### 4️⃣  容器健康检查
```bash
docker ps | grep alert-management
docker logs -f docker_alert-management_1
curl http://localhost:8082/actuator/health
```

**预期结果**:
- ✅ 容器运行中
- ✅ 日志无错误
- ✅ 健康端点返回 UP

### 5️⃣  配置验证
```bash
# 检查环境变量应用
docker exec docker_alert-management_1 env | grep LOGGING
docker exec docker_alert-management_1 env | grep SPRING_
```

**预期结果**:
- ✅ LOGGING_LEVEL_ROOT=WARN
- ✅ SPRING_PROFILES_ACTIVE=docker

---

## 📊 验证结果记录

| 检查项 | 状态 | 时间 | 备注 |
|--------|------|------|------|
| 环境检查 | ✅ | 2025-10-22 | 所有工具就位 |
| Maven 编译 | ⏳ | - | 待执行 |
| Docker 构建 | ⏳ | - | 待执行 |
| 容器运行 | ⏳ | - | 待执行 |
| 配置验证 | ⏳ | - | 待执行 |
| **总体状态** | **⏳** | **进行中** | **预计 15-20 分钟** |

---

## 🔍 故障排查指南

### 问题 1: 脚本权限不足
```bash
chmod +x scripts/rebuild.sh
```

### 问题 2: Maven 编译失败
```bash
# 清理并重试
mvn clean -U
mvn compile -DskipTests
```

### 问题 3: Docker 构建失败
```bash
# 检查 docker/.env
cat docker/.env

# 验证 docker-compose.yml
docker-compose config | grep -A 10 cache_from

# 手动运行 docker-compose
cd docker && docker-compose build alert-management --no-cache
```

### 问题 4: 容器启动失败
```bash
# 查看详细日志
docker logs -f docker_alert-management_1

# 检查健康状态
docker inspect docker_alert-management_1 | grep -A 10 Health
```

### 问题 5: 缓存问题仍存在
```bash
# 确认 BUILD_DATE 每次不同
grep BUILD_DATE docker/.env

# 完整系统清理
docker system prune -a --volumes
bash scripts/rebuild.sh alert-management full
```

---

## ✨ 成功标志

当以下条件全部满足时，验证成功 ✅:

1. **Maven 编译**: `BUILD SUCCESS` 无错误
2. **Docker 构建**: 完成，新镜像生成
3. **容器运行**: 状态为 `healthy`
4. **健康检查**: 端点返回 HTTP 200 UP
5. **日志输出**: WARN 级别，无 DEBUG 消息
6. **配置生效**: 环境变量正确应用
7. **性能指标**: 快速重建 < 5 分钟
8. **缓存验证**: 每次构建都使用新镜像层

---

## 📝 下一步

- [ ] 执行验证计划中的所有 6 个阶段
- [ ] 记录每个阶段的结果
- [ ] 生成最终验证报告
- [ ] 分享最佳实践给团队
- [ ] 规划 Phase 2: DTO Pattern Implementation

---

**验证负责人**: GitHub Copilot  
**验证日期**: 2025-10-22  
**状态**: 进行中 🔄
