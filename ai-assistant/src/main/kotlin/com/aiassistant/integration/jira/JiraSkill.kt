package com.aiassistant.integration.jira

import com.aiassistant.skill.*

class JiraSkill(
    private val client: JiraClient,
    private val defaultProject: String = "PROJ"
) : Skill {

    override val name = "jira"
    override val description = "Jira integration for task management"

    override fun canHandle(request: SkillRequest): Boolean {
        val msg = request.message.lowercase()
        return msg.contains("jira") ||
               msg.contains("задач") ||
               msg.contains("issue") ||
               msg.contains("тикет") ||
               msg.contains("task") ||
               msg.startsWith("/jira")
    }

    override suspend fun handle(request: SkillRequest): SkillResponse {
        val message = request.message

        return try {
            when {
                message.startsWith("/jira search ") -> handleSearch(message)
                message.startsWith("/jira create ") -> handleCreate(message)
                message.startsWith("/jira get ") -> handleGet(message)
                message.startsWith("/jira status ") -> handleStatus(message)
                message == "/jira help" -> help()
                message == "/jira" -> help()
                else -> handleNaturalLanguage(request)
            }
        } catch (e: Exception) {
            SkillResponse(false, "Error: ${e.message}", skillName = name)
        }
    }

    private suspend fun handleSearch(message: String): SkillResponse {
        val query = message.removePrefix("/jira search ").trim()
        if (query.isBlank()) {
            return SkillResponse(false, "Usage: /jira search <jql query>", name = name)
        }

        val result = client.searchIssues(query)
        return if (result.isSuccess) {
            val issues = result.getOrNull() ?: emptyList()
            if (issues.isEmpty()) {
                SkillResponse(true, "No issues found", name = name)
            } else {
                val text = issues.joinToString("\n") { "${it.key}: ${it.summary}" }
                SkillResponse(true, text, issues, name)
            }
        } else {
            SkillResponse(false, "Search failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleCreate(message: String): SkillResponse {
        val args = message.removePrefix("/jira create ").split("|", limit = 3)
        if (args.size < 2) {
            return SkillResponse(false, "Usage: /jira create <summary> | <description>", name = name)
        }

        val summary = args[0].trim()
        val description = args.getOrNull(1)?.trim() ?: ""
        val project = args.getOrNull(2)?.trim() ?: defaultProject

        val result = client.createIssue(project, summary, description)
        return if (result.isSuccess) {
            val issue = result.getOrNull()
            SkillResponse(true, "Created: ${issue?.key} - ${issue?.summary}", issue, name)
        } else {
            SkillResponse(false, "Failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleGet(message: String): SkillResponse {
        val key = message.removePrefix("/jira get ").trim()
        if (key.isBlank()) {
            return SkillResponse(false, "Usage: /jira get <issue-key>", name = name)
        }

        val result = client.getIssue(key)
        return if (result.isSuccess) {
            val issue = result.getOrNull()
            val text = """
                |${issue?.key}: ${issue?.summary}
                |Status: ${issue?.status}
                |Priority: ${issue?.priority ?: "Not set"}
                |Assignee: ${issue?.assignee ?: "Unassigned"}
                |Description: ${issue?.description ?: "No description"}
            """.trimMargin()

            SkillResponse(true, text, issue, name)
        } else {
            SkillResponse(false, "Issue not found: $key", name = name)
        }
    }

    private suspend fun handleStatus(message: String): SkillResponse {
        val key = message.removePrefix("/jira status ").trim()
        if (key.isBlank()) {
            return SkillResponse(false, "Usage: /jira status <issue-key>", name = name)
        }

        val result = client.getIssue(key)
        return if (result.isSuccess) {
            val issue = result.getOrNull()
            SkillResponse(true, "${issue?.key} status: ${issue?.status}", name = name)
        } else {
            SkillResponse(false, "Issue not found", name = name)
        }
    }

    private suspend fun handleNaturalLanguage(request: SkillRequest): SkillResponse {
        val msg = request.message.lowercase()

        val createPatterns = listOf("создай", "create", "новая задача", "new task")
        val searchPatterns = listOf("найди", "search", "покажи", "show", "список")

        return when {
            createPatterns.any { msg.contains(it) } -> {
                val summary = request.message
                    .replace(Regex("создай|create|новая задача|new task|jira", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (summary.isBlank()) {
                    return SkillResponse(false, "Укажи название задачи", name = name)
                }
                handleCreate("/jira create $summary | $summary")
            }
            searchPatterns.any { msg.contains(it) } -> {
                val query = msg
                    .replace(Regex("найди|search|покажи|show|список|jira", RegexOption.IGNORE_CASE), "")
                    .trim()
                val jql = if (query.isNotBlank()) "text ~ \"$query\"" else "assignee = currentUser() ORDER BY updated DESC"
                handleSearch("/jira search $jql")
            }
            else -> help()
        }
    }

    private fun help(): SkillResponse {
        return SkillResponse(
            true,
            """
            |Jira Skills:
            |  /jira search <jql>     - Search issues
            |  /jira create <summary> | <description> | [project] - Create issue
            |  /jira get <key>       - Get issue details
            |  /jira status <key>    - Show issue status
            |  /jira help            - Show this help
            |
            |Examples:
            |  /jira search assignee = currentUser()
            |  /jira create Test task | Description here | PROJ
            |  найди задачи про тестирование
            """.trimMargin(),
            name = name
        )
    }
}
