# API Gateway 快速参考

**端口**: 8888 | **技术**: Spring Cloud Gateway | **状态**: ✅ 完成

---

## 🚀 快速开始

```bash
# 一键部署
./scripts/deploy_api_gateway.sh

# 运行测试
./scripts/test_api_gateway.sh

# 查看日志
docker-compose logs -f api-gateway
```

---

## 📋 路由规则

| 路径 | 服务 | 端口 |
|------|------|------|
| `/api/v1/customers/**` | customer-management | 8084 |
| `/api/v1/devices/**` | customer-management | 8084 |
| `/api/v1/notifications/**` | customer-management | 8084 |
| `/api/v1/logs/**` | data-ingestion | 8080 |
| `/api/v1/assessment/**` | threat-assessment | 8081 |
| `/api/v1/alerts/**` | alert-management | 8082 |

---

## 🔧 常用命令

```bash
# 健康检查
curl http://localhost:8888/actuator/health

# 查看路由
curl http://localhost:8888/actuator/gateway/routes | jq

# 查询客户
curl http://localhost:8888/api/v1/customers

# 创建客户
curl -X POST http://localhost:8888/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{"customerId":"test","companyName":"测试","contactEmail":"test@example.com","subscriptionTier":"PROFESSIONAL"}'

# 查询告警
curl "http://localhost:8888/api/v1/alerts?page=0&size=10"
```

---

## 🛡️ 核心特性

- ✅ **6个路由规则** - 覆盖所有API
- ✅ **限流保护** - 100容量，10/s填充
- ✅ **熔断降级** - 4个降级端点
- ✅ **CORS跨域** - 支持前端访问
- ✅ **请求日志** - 记录所有请求
- ✅ **监控指标** - Prometheus集成

---

## 📊 监控端点

```bash
# 健康状态
curl http://localhost:8888/actuator/health

# 性能指标
curl http://localhost:8888/actuator/metrics

# Prometheus格式
curl http://localhost:8888/actuator/prometheus

# 路由配置
curl http://localhost:8888/actuator/gateway/routes
```

---

## 🐛 故障排查

```bash
# 1. 查看日志
docker-compose logs api-gateway

# 2. 检查容器
docker-compose ps

# 3. 验证后端服务
curl http://localhost:8084/api/v1/customers

# 4. 检查网络
docker-compose exec api-gateway ping customer-management
```

---

## 🔄 限流配置

**默认**: 100容量, 10/s填充  
**修改**: docker-compose.yml → environment

```yaml
environment:
  RATE_LIMIT_CAPACITY: 200
  RATE_LIMIT_REFILL_RATE: 20
```

---

## 📖 完整文档

- **详细文档**: `docs/api/api_gateway_current.md`
- **实现总结**: `services/api-gateway/IMPLEMENTATION_SUMMARY.md`
- **原始README**: `services/api-gateway/README.md`

---

## 🎯 下一步

- [ ] 部署测试
- [ ] 性能压测
- [ ] JWT认证
- [ ] Redis限流
- [ ] 前端开发
