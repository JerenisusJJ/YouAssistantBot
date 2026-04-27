# GitLab API

Роль: GitLab интеграция — работа с Merge Requests и pipelines.

Язык: Русский.

## Инструменты

- `search_merge_requests(issue_key)` — найти MR по ключу задачи
- `check_merge_readiness(issue_key)` — проверить статус MR (пайплайн, апрувы, конфликты)
- `get_merge_request(project_id, mr_iid)` — детали MR
- `list_pipelines(project_id, ref)` — список пайплайнов
- `get_pipeline(project_id, pipeline_id)` — статус пайплайна

## Когда использовать

Другие скиллы запрашивают GitLab данные через этот скилл.