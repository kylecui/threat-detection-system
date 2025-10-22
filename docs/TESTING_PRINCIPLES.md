# 🧪 测试原则

**更新日期**: 2025-10-22
**原则**: 一切测试以容器化方式运行

---

## 🎯 核心原则

### 容器化测试第一原则
**所有测试必须在 Docker 容器环境中运行**，确保测试环境与生产环境完全一致。

**理由**:
- 消除"在我机器上能运行"的问题
- 保证环境一致性
- 简化 CI/CD 流程
- 提高测试可靠性

---

## 🏗️ 测试环境架构

### Docker Compose 测试环境
```yaml
# docker/docker-compose.yml
version: '3.8'
services:
  postgres:
    image: postgres:15-alpine
  kafka:
    image: confluentinc/cp-kafka:7.4.0
  # ... 其他服务
```

### 测试执行流程
```bash
# 1. 启动测试环境
docker-compose -f docker/docker-compose.yml up -d

# 2. 等待服务就绪 (60秒)
sleep 60

# 3. 执行测试
bash scripts/test_backend_api_happy_path.sh

# 4. 清理环境
docker-compose -f docker/docker-compose.yml down
```

---

## 📊 测试分类

### 1. 单元测试 (Unit Tests)
- **执行环境**: Maven + JUnit
- **容器化**: 通过 Docker 构建执行
- **覆盖范围**: 核心业务逻辑
- **命令**:
  ```bash
  docker run --rm -v $(pwd):/app -w /app maven:3.8.7-openjdk-21 \
    mvn clean test
  ```

### 2. 集成测试 (Integration Tests)
- **执行环境**: Docker Compose
- **覆盖范围**: 服务间通信
- **测试脚本**: `scripts/test_backend_api_happy_path.sh`
- **当前状态**: 57/58 API 通过 (98.3%)

### 3. API 测试 (API Tests)
- **执行环境**: Docker Compose
- **工具**: curl + jq
- **覆盖范围**: 所有 REST API 端点
- **报告生成**: JSON + HTML 报告

### 4. 容器化测试 (Container Tests)
- **执行环境**: Docker Compose
- **验证内容**: 镜像构建、服务启动、健康检查
- **性能测试**: 吞吐量和延迟验证

---

## 🎯 测试标准

### API 测试标准
- **响应时间**: < 1秒 (平均)
- **成功率**: > 98%
- **错误处理**: 正确的 HTTP 状态码
- **数据验证**: 请求/响应格式正确

### 容器测试标准
- **启动时间**: < 60秒 (所有服务)
- **健康检查**: 100% 通过
- **资源使用**: CPU < 80%, 内存 < 80%
- **日志**: 无错误日志

### 性能测试标准
- **吞吐量**: 符合设计目标
- **延迟**: 符合 SLA 要求
- **并发**: 支持设计负载

---

## 📋 测试执行清单

### 每日测试 (Daily)
- [x] Docker Compose 启动验证
- [x] 服务健康检查
- [x] API 端点连通性测试
- [x] 基础功能验证

### 构建测试 (Build)
- [x] Maven 编译成功
- [x] Docker 镜像构建成功
- [x] 容器启动成功
- [x] 单元测试通过

### 发布测试 (Release)
- [x] 完整 API 测试套件
- [x] 性能基准测试
- [x] 安全漏洞扫描
- [x] 文档更新验证

---

## 🔧 测试工具链

### 核心工具
- **测试框架**: JUnit 5 + Mockito
- **API测试**: curl + jq + bash
- **容器化**: Docker + Docker Compose
- **构建工具**: Maven 3.8.7
- **报告生成**: JSON + HTML

### 测试脚本
- `scripts/test_backend_api_happy_path.sh`: 主要 API 测试脚本
- `scripts/test_api_gateway.sh`: API 网关测试
- `scripts/run_tests.sh`: 完整测试套件

---

## 📈 测试结果跟踪

### 当前测试状态
```
总测试数: 58 个 API 端点
通过: 57 个 (98.3%)
失败: 1 个 (/assessment/evaluate)
测试环境: Docker Compose
测试脚本: test_backend_api_happy_path.sh
```

### 历史趋势
- **Day 1**: 基础功能验证 ✅
- **Day 2**: API 端点测试 (80% 通过)
- **Day 3**: 集成测试完善 (95% 通过)
- **Day 4**: 性能优化 (98% 通过)
- **Day 5**: 生产就绪验证 (98.3% 通过)

---

## 🚨 测试失败处理

### 自动重试机制
```bash
# 测试失败时自动重试 3 次
for i in {1..3}; do
  if run_test; then
    break
  fi
  echo "Retry $i/3..."
  sleep 10
done
```

### 失败分类
1. **环境问题**: 服务未启动 → 重启 Docker Compose
2. **网络问题**: 连接超时 → 检查端口映射
3. **数据问题**: 数据库连接失败 → 验证 PostgreSQL
4. **代码问题**: 逻辑错误 → 修复代码后重新测试

### 紧急处理
- **CI/CD 失败**: 立即通知开发团队
- **生产影响**: 回滚到上一个稳定版本
- **数据丢失**: 执行备份恢复流程

---

## 📚 测试文档

### 测试文档位置
- `docs/testing/`: 测试相关文档
- `scripts/`: 测试脚本
- `test_report_*.json`: 测试结果 (自动生成)
- `test_report_*.html`: 测试报告 (自动生成)

### 文档清单
- `BACKEND_API_INVENTORY.md`: API 清单
- `BACKEND_API_TESTING_DAY2_SUMMARY.md`: 测试总结
- `COMPREHENSIVE_DEVELOPMENT_TEST_PLAN.md`: 测试计划
- `BACKEND_TEST_ACTION_PLAN.md`: 测试行动计划

---

## 🎯 最佳实践

### 测试编写原则
1. **独立性**: 每个测试独立运行
2. **可重复性**: 多次运行结果一致
3. **自动化**: 无人工干预
4. **快速**: 单个测试 < 1秒

### 环境管理
1. **隔离**: 测试环境与开发/生产隔离
2. **一致性**: 所有环境配置相同
3. **可重现**: 可以随时重建环境
4. **可监控**: 实时监控测试状态

### 持续改进
1. **覆盖率**: 持续提高测试覆盖率
2. **效率**: 优化测试执行时间
3. **可靠性**: 减少测试假阳性
4. **可维护性**: 简化测试代码维护

---

## 📞 支持与联系

**测试负责人**: QA Team
**技术支持**: dev-support@company.com
**文档维护**: docs@company.com

---

*容器化测试确保环境一致性和部署可靠性*
