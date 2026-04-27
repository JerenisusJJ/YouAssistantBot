# Allure API

Роль: Allure TestOps интеграция — работа с тестовыми ранами и результатами.

Язык: Русский.

## Инструменты

- `list_launches(project_id, limit)` — список запусков
- `get_launch_stats(launch_id)` — статистика (passed/failed/broken)
- `list_test_results(launch_id, status, limit)` — список результатов
- `get_test_result(id)` — детали теста

## Когда использовать

Другие скиллы запрашивают Allure данные через этот скилл.