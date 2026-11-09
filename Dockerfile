FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    ROADIO_ALLOW_PUBLIC_BIND=1 \
    ROADIO_DEPLOYMENT_MODE=railway-demo

WORKDIR /app

COPY server ./server
COPY web ./web

EXPOSE 8080

CMD ["sh", "-c", "python3 server/app.py --data-dir \"${RAILWAY_VOLUME_MOUNT_PATH:-/data}\" serve --host 0.0.0.0 --port \"${PORT:-8080}\""]
