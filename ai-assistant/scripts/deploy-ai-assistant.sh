#!/bin/bash
# deploy-ai-assistant.sh - Раскатка AI Assistant на dev20

set -e

APP_DIR="/opt/ai-assistant"
REPO_URL="https://github.com/JerenisusJJ/YouAssistantBot.git"

echo "=== Раскатка AI Assistant на dev20 ==="

# Check if directory exists
if [ ! -d "$APP_DIR" ]; then
    echo "[1] Клонируем репозиторий..."
    mkdir -p $APP_DIR
    git clone $REPO_URL $APP_DIR
    cd $APP_DIR
else
    echo "[1] Обновляем код..."
    cd $APP_DIR
    git fetch origin
    git pull origin master
fi

# Check if .env exists
if [ ! -f "$APP_DIR/.env" ]; then
    echo "[2] Создаём .env из примера..."
    cp $APP_DIR/.env.example $APP_DIR/.env
    echo "    ВНИМАНИЕ: Отредактируй .env перед запуском!"
    echo "    Токены: TELEGRAM_BOT_TOKEN, CAILA_TOKEN, и др."
    nano $APP_DIR/.env
    read -p "Нажми Enter когда .env готов..."
fi

# Check docker-compose
if [ ! -f "$APP_DIR/docker-compose.yml" ]; then
    echo "[ERROR] docker-compose.yml не найден!"
    exit 1
fi

# Build and start
echo "[3] Собираем образ..."
docker-compose build --no-cache

echo "[4] Запускаем..."
docker-compose up -d

# Wait for startup
echo "[5] Ждём запуска..."
sleep 10

# Show status
echo ""
echo "=== Статус контейнеров ==="
docker-compose ps

echo ""
echo "=== Логи (последние 50 строк) ==="
docker-compose logs --tail=50

echo ""
echo "=== Готово! ==="
echo "Логи: docker-compose logs -f"
echo "Стоп: docker-compose down"
echo "Перезапуск: docker-compose restart"