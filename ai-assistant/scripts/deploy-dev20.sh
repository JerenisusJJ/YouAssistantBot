#!/bin/bash
# Deploy to dev20 - pulls latest code and restarts container without touching DB/data

set -e

REPO_URL="https://github.com/JerenisusJJ/YouAssistantBot.git"
APP_DIR="/opt/ai-assistant"
SERVICE_NAME="ai-assistant"

echo "=== Раскатка на dev20 ==="

# Check if directory exists
if [ ! -d "$APP_DIR" ]; then
    echo "[1] Клонируем репозиторий..."
    git clone $REPO_URL $APP_DIR
    cd $APP_DIR
else
    echo "[1] Обновляем код..."
    cd $APP_DIR
    git pull origin master
fi

# Check if .env exists (don't overwrite)
if [ ! -f "$APP_DIR/.env" ]; then
    echo "[2] ВНИМАНИЕ: .env не найден! Создайте вручную"
    echo "Копируйте пример: cp .env.example .env"
fi

# Check if docker-compose.yml exists
if [ ! -f "$APP_DIR/docker-compose.yml" ]; then
    echo "[ERROR] docker-compose.yml не найден!"
    exit 1
fi

# Pull latest images
echo "[3] Pull последних образов..."
docker-compose pull

# Build only if needed (--build flag)
echo "[4] Собираем..."
docker-compose build --no-cache

# Stop current container
echo "[5] Останавливаем контейнер..."
docker-compose down || true

# Start with new code
echo "[6] Запускаем..."
docker-compose up -d

# Check status
echo "[7] Проверяем статус..."
docker-compose ps

echo "=== Готово! ==="
echo "Логи: docker-compose logs -f"
echo "Стоп: docker-compose down"