FROM python:3.11-slim

WORKDIR /app

ARG USE_CHINA_MIRROR=false
RUN if [ "$USE_CHINA_MIRROR" = "true" ]; then \
      sed -i \
        -e 's|http://deb.debian.org/debian-security|https://mirrors.aliyun.com/debian-security|g' \
        -e 's|http://deb.debian.org/debian|https://mirrors.aliyun.com/debian|g' \
        /etc/apt/sources.list.d/debian.sources; \
    fi && \
    apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

COPY requirements-runtime.txt .

RUN if [ "$USE_CHINA_MIRROR" = "true" ]; then \
      pip install --no-cache-dir \
        -i https://mirrors.aliyun.com/pypi/simple/ \
        --trusted-host mirrors.aliyun.com \
        -r requirements-runtime.txt; \
    else \
      pip install --no-cache-dir -r requirements-runtime.txt; \
    fi && \
    pip install --no-cache-dir --no-deps \
      --index-url https://download.pytorch.org/whl/cpu \
      'torch==2.2.2+cpu'

COPY app/ ./app/
COPY models/ ./models/

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=30s \
    CMD curl -fsS http://localhost:8086/health || exit 1

EXPOSE 8086

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8086"]
