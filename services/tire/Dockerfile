FROM python:3.12-slim

WORKDIR /app

# Install dependencies first (layer caching)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application source
COPY app/         ./app/
COPY adapters/    ./adapters/
COPY admin/       ./admin/
COPY analyzers/   ./analyzers/
COPY cache/       ./cache/
COPY collectors/  ./collectors/
COPY config/      ./config/
COPY enrichers/   ./enrichers/
COPY graph/       ./graph/
COPY locales/     ./locales/
COPY models/      ./models/
COPY normalizers/ ./normalizers/
COPY plugins/     ./plugins/
COPY reporters/   ./reporters/
COPY rules/       ./rules/
COPY storage/     ./storage/
COPY templates/   ./templates/

# Create non-root user and data directories
RUN groupadd -r tire && useradd -r -g tire tire \
    && mkdir -p /app/data /app/admin \
    && chown -R tire:tire /app

USER tire

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    LOG_LEVEL=INFO \
    CACHE_TTL_HOURS=24 \
    HTTP_TIMEOUT_SECONDS=15 \
    MAX_RETRIES=2

HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/healthz')" || exit 1

EXPOSE 8000

CMD ["uvicorn", "app.api:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2", "--access-log"]
