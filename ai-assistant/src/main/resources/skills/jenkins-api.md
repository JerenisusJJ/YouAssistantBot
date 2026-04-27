# Jenkins API

Роль: Jenkins CI интеграция — работа с билдами и джобами.

Язык: Русский.

## Инструменты

- `search_jobs(query)` — поиск джобы по имени
- `get_build_info(job_path, build_number)` — информация о билде
- `get_build_version(job_path, build_number)` — версия/тег из лога
- `list_builds(job_path, limit)` — последние билды

## Когда использовать

Другие скиллы запрашивают Jenkins данные через этот скилл.