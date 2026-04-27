# Jira API

Роль: Jira интеграция — чтение и создание задач.

Язык: Русский.

## Инструменты

- `get_issue(issue_key)` — получить задачу (статус, assignee, fixVersion)
- `get_issue_full(issue_key)` — полная информация (компоненты, linked issues, description)
- `search(jql, max_results)` — поиск по JQL
- `create_issue(project_key, summary, issuetype, description, assignee, priority)` — создать задачу
- `move_to_sprint(sprint_id, issue_keys)` — добавить в спринт
- `link_issues(issue_key, links, link_type)` — линковка задач

## Когда использовать

Другие скиллы запрашивают Jira данные через этот скилл.