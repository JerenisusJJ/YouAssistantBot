package com.aiassistant.integration.jira

import com.aiassistant.skill.*

class JiraServerSkill(
    private val client: JiraServerClient,
    private val defaultProject: String = "PROJ"
) : Skill {

    override val name = "jira-server"
    override val description = "Jira Server (self-hosted) integration"

    override fun canHandle(request: SkillRequest): Boolean {
        val msg = request.message.lowercase()
        return msg.contains("jira-server") ||
               msg.contains("jira on-premise") ||
               msg.startsWith("/jira-server")
    }

    override suspend fun handle(request: SkillRequest): SkillResponse {
        val message = request.message

        return try {
            when {
                message.startsWith("/jira-server search ") -> handleSearch(message)
                message.startsWith("/jira-server create ") -> handleCreate(message)
                message.startsWith("/jira-server get ") -> handleGet(message)
                message.startsWith("/jira-server transition ") -> handleTransition(message)
                message.startsWith("/jira-server transitions ") -> handleTransitions(message)
                message.startsWith("/jira-server projects") -> handleProjects(message)
                message == "/jira-server help" -> help()
                message == "/jira-server" -> help()
                else -> handleNaturalLanguage(request)
            }
        } catch (e: Exception) {
            SkillResponse(false, "Error: ${e.message}", skillName = name)
        }
    }

    private suspend fun handleSearch(message: String): SkillResponse {
        val query = message.removePrefix("/jira-server search ").trim()
        if (query.isBlank()) {
            return SkillResponse(false, "Usage: /jira-server search <jql>", name = name)
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
        val args = message.removePrefix("/jira-server create ").split("|", limit = 4)
        if (args.size < 2) {
            return SkillResponse(false, "Usage: /jira-server create <summary> | <description> | [project] | [type]", name = name)
        }

        val summary = args[0].trim()
        val description = args.getOrNull(1)?.trim() ?: ""
        val project = args.getOrNull(2)?.trim() ?: defaultProject
        val issueType = args.getOrNull(3)?.trim() ?: "Task"

        val result = client.createIssue(project, summary, description, issueType)
        return if (result.isSuccess) {
            val issue = result.getOrNull()
            SkillResponse(true, "Created: ${issue?.key} - ${issue?.summary}", issue, name)
        } else {
            SkillResponse(false, "Failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleGet(message: String): SkillResponse {
        val key = message.removePrefix("/jira-server get ").trim()
        if (key.isBlank()) {
            return SkillResponse(false, "Usage: /jira-server get <issue-key>", name = name)
        }

        val result = client.getIssue(key)
        return if (result.isSuccess) {
            val issue = result.getOrNull()
            val text = """
                |${issue?.key}: ${issue?.summary}
                |Type: ${issue?.issueType}
                |Status: ${issue?.status}
                |Priority: ${issue?.priority ?: "Not set"}
                |Assignee: ${issue?.assignee ?: "Unassigned"}
                |---
                |${issue?.description?.take(500) ?: "No description"}
            """.trimMargin()

            SkillResponse(true, text, issue, name)
        } else {
            SkillResponse(false, "Issue not found", name = name)
        }
    }

    private suspend fun handleTransitions(message: String): SkillResponse {
        val key = message.removePrefix("/jira-server transitions ").trim()
        if (key.isBlank()) {
            return SkillResponse(false, "Usage: /jira-server transitions <issue-key>", name = name)
        }

        val result = client.getTransitions(key)
        return if (result.isSuccess) {
            val transitions = result.getOrNull() ?: emptyList()
            val text = transitions.joinToString("\n") { "${it.id}: ${it.name} -> ${it.toStatus}" }
            SkillResponse(true, text, transitions, name)
        } else {
            SkillResponse(false, "Failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleTransition(message: String): SkillResponse {
        val args = message.removePrefix("/jira-server transition ").trim().split(" ")
        if (args.size < 2) {
            return SkillResponse(false, "Usage: /jira-server transition <issue-key> <transition-id>", name = name)
        }

        val key = args[0]
        val transitionId = args[1]

        val result = client.transitionIssue(key, transitionId)
        return if (result.isSuccess) {
            SkillResponse(true, "Issue transitioned: $key", name = name)
        } else {
            SkillResponse(false, "Failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleProjects(message: String): SkillResponse {
        val result = client.getProjects()
        return if (result.isSuccess) {
            val projects = result.getOrNull() ?: emptyList()
            val text = projects.joinToString("\n") { "${it.key}: ${it.name}" }
            SkillResponse(true, text, projects, name)
        } else {
            SkillResponse(false, "Failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleNaturalLanguage(request: SkillRequest): SkillResponse {
        val msg = request.message.lowercase()

        return when {
            msg.contains("создай") || msg.contains("create") -> {
                val summary = request.message
                    .replace(Regex("создай|create|jira-server", RegexOption.IGNORE_CASE), "")
                    .trim()
                handleCreate("/jira-server create $summary | Auto description")
            }
            msg.contains("найди") || msg.contains("search") -> {
                val query = msg
                    .replace(Regex("найди|search|jira-server", RegexOption.IGNORE_CASE), "")
                    .trim()
                val jql = if (query.isNotBlank()) "text ~ \"$query\"" else "assignee = currentUser() ORDER BY updated DESC"
                handleSearch("/jira-server search $jql")
            }
            msg.contains("мои задачи") -> {
                handleSearch("/jira-server search assignee = currentUser() AND resolution = Unresolved ORDER BY updated DESC")
            }
            else -> help()
        }
    }

    private fun help(): SkillResponse {
        return SkillResponse(
            true,
            """
            |Jira Server (self-hosted) Skills:
            |  /jira-server search <jql>        - Search issues
            |  /jira-server create <summary> | <desc> | [project] | [type] - Create issue
            |  /jira-server get <key>        - Get issue details
            |  /jira-server transitions <key> - List available transitions
            |  /jira-server transition <key> <id> - Transition issue
            |  /jira-server projects         - List projects
            |  /jira-server help             - Show this help
            |
            |Examples:
            |  /jira-server search assignee = me
            |  /jira-server create Test task | Description | PROJ
            |  мои задачи
            """.trimMargin(),
            name = name
        )
    }
}