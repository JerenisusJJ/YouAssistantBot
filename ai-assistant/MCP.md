# MCP (Model Context Protocol) Integration

AI Assistant поддерживает подключение к MCP серверам для расширения функциональности.

## Поддерживаемые MCP серверы

### Atlassian Rovo MCP (официальный)
- **URL**: `https://mcp.atlassian.com/v1/mcp`
- **Функции**: Jira, Confluence, Compass
- **Авторизация**: OAuth через Atlassian

### Bridge-MCP
- **URL**: `http://localhost:8000/mcp` (self-hosted)
- **Функции**: Jira, GitLab, Confluence
- **Авторизация**: API токен

## Быстрый старт

### 1. Atlassian Rovo MCP

```bash
# Настройка через Claude Desktop или другой MCP клиент
```

### 2. Bridge-MCP (self-hosted)

```bash
pip install bridge-mcp
bridge-mcp --config config.yaml
```

## Конфигурация

### Переменные окружения

```bash
# Atlassian MCP
ATLASSIAN_MCP_URL=https://mcp.atlassian.com/v1/mcp
ATLASSIAN_CLOUD_ID=your-cloud-id
ATLASSIAN_OAUTH_TOKEN=your-token

# Bridge-MCP (self-hosted)
BRIDGE_MCP_URL=http://localhost:8000/mcp
BRIDGE_MCP_TOKEN=your-api-token
```

## Доступные инструменты

### Atlassian MCP Tools

| Tool | Описание |
|------|----------|
| `jira_search` | Поиск задач |
| `jira_create_issue` | Создание задачи |
| `jira_get_issue` | Получить задачу |
| `confluence_search` | Поиск страниц |
| `confluence_create_page` | Создание страницы |
| `confluence_summarize` | Суммаризация |

### GitLab MCP Tools

| Tool | Описание |
|------|----------|
| `gitlab_search` | Поиск по коду |
| `gitlab_list_merge_requests` | Список MR |
| `gitlab_list_pipelines` | Список pipelines |

## Использование через бота

```
/atlassian search bug in PROJ
/atlassian create issue Test task | Description | PROJ
/confluence search инструкция

/gitlab-mcp search fix bug
/gitlab-mcp mr my-project
```

## Ссылки

- [Atlassian MCP Documentation](https://www.atlassian.com/platform/remote-mcp-server)
- [Bridge-MCP](https://github.com/ali-moghadam/Bridge-MCP)
- [MCP Registry](https://glama.ai/mcp/servers)