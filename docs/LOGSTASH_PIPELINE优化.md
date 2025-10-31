
# ============================================================================
# Pipeline配置说明 (优化版)
# ============================================================================
#
# 架构优化:
#   - 移除冗余验证逻辑 (data-ingestion已处理)
#   - 保持snake_case字段名 (HTTP API规范)
#   - 专注于ETL，业务逻辑交给应用层
#
# 数据流向:
#   rsyslog (TCP 9080)
#   → Logstash基础解析 (key=value → 结构化)
#   → HTTP POST到data-ingestion服务
#       - 攻击日志: /api/v1/logs/ingest
#       - 心跳日志: /api/v1/logs/ingest
#   → data-ingestion处理验证和业务逻辑
#   → Kafka消息发送 (camelCase格式)
#
# 字段命名:
#   - logstash保持snake_case (HTTP传输)
#   - data-ingestion解析后转换为camelCase (Java对象)
#   - Kafka消息使用camelCase (序列化规范)
#
# 性能优化:
#   - 减少filter复杂度，提升吞吐量
#   - HTTP直接输出，减少中间环节
#   - 异步重试机制，提高可靠性
#
# ============================================================================