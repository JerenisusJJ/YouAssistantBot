# AI Assistant

Персональный AI-ассистент с системой скиллов.

## Архитектура

```
┌─────────────────────────────────────────────┐
│            Mattermost/Telegram               │
│              (Adapter Layer)                │
└─────────────────┬───────────────────────────┘
                  │
┌─────────────────▼───────────────────────────┐
│              Admin Skill (Router)            │
│  • work/personal classification            │
│  • skill selection                          │
└─────────────────┬───────────────────────────┘
                  │
        ┌─────────┴─────────┐
        ▼                 ▼
   ┌─────────┐       ┌─────────┐
   │  Work   │       │Personal │
   │ Skills  │       │ Skills  │
   └─────────┘       └─────────┘
```

## Скиллы

| Скилл | Назначение | Статус |
|-------|------------|--------|
| `admin` | Роутер work/personal | ✅ |
| `db` | CRUD для конфигов | ✅ |
| `mattermost` | Интеграция с Mattermost | ✅ |
| `jira` | Jira интеграция | 🔜 |
| `gitlab` | GitLab интеграция | 🔜 |
| `confluence` | Confluence интеграция | 🔜 |

## Запуск

### Локально (требуется PostgreSQL)

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=ai_assistant
export DB_USER=ai_user
export DB_PASSWORD=your_password
export ENCRYPT_KEY=your-secret-key

gradle run
```

### Docker

```bash
docker-compose up -d
```

## Конфигурация

### Переменные окружения

| Переменная | Описание | По умолчанию |
|------------|----------|--------------|
| `DB_HOST` | PostgreSQL хост | localhost |
| `DB_PORT` | PostgreSQL порт | 5432 |
| `DB_NAME` | Имя БД | ai_assistant |
| `DB_USER` | Пользователь БД | ai_user |
| `DB_PASSWORD` | Пароль БД | - |
| `ENCRYPTION_KEY` | Ключ для шифрования | - |
| `MATTERMOST_URL` | URL Mattermost | - |
| `MATTERMOST_TOKEN` | Токен бота | - |
| `CAILA_TOKEN` | Токен Caila API | - |

## Деплой

### Бэкап

```bash
./scripts/backup.sh --output ./backups/backup-$(date +%Y%m%d).tar.gz
```

### Восстановление на хосте

```bash
./scripts/deploy.sh --host user@stend --backup ./backups/backup-20240421.tar.gz
```

## Тесты

```bash
gradle test
```

Coverage отчёт: `build/reports/jacoco/jacocoHtml/index.html`

## Структура проекта

```
src/
├── main/kotlin/com/aiassistant/
│   ├── Application.kt           # Точка входа
│   ├── skill/                   # Skill system
│   │   ├── Skill.kt            # Base interface
│   │   ├── SkillRegistry.kt    # Registry
│   │   └── admin/              # Admin skill
│   ├── db/                     # Database skill
│   └── integration/            # External integrations
│       └── mattermost/         # Mattermost
└── test/kotlin/               # Тесты
    └── ...                     # Unit & Integration тесты
```
