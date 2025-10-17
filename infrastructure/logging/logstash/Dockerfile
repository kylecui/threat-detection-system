# Logstash Dockerfile for Threat Detection System
# 基于官方镜像，支持TCP/TLS接收rsyslog

FROM docker.elastic.co/logstash/logstash:8.11.0

# Kafka插件已内置在 logstash-integration-kafka 中，无需额外安装

# 创建配置目录
RUN mkdir -p /usr/share/logstash/config /usr/share/logstash/pipeline /usr/share/logstash/certs

# 复制配置文件
COPY logstash.yml /usr/share/logstash/config/
COPY pipeline/ /usr/share/logstash/pipeline/

# 创建TLS证书目录和测试日志目录
RUN mkdir -p /usr/share/logstash/certs /tmp/production_test_logs && \
    chown -R logstash:logstash /usr/share/logstash/certs /tmp/production_test_logs

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:9600/_node/stats || exit 1

# 使用非root用户运行
USER logstash

# 暴露端口
# 9080: TCP rsyslog输入（无TLS）
# 9081: TCP rsyslog输入（TLS，未来使用）
# 9600: 监控API
EXPOSE 9080 9081 9600

ENTRYPOINT ["/usr/local/bin/docker-entrypoint"]
