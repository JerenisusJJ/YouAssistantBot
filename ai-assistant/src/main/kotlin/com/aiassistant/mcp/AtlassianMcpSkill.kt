package com.aiassistant.mcp

import com.aiassistant.skill.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

class AtlassianMcpSkill(
    private val mcpClient: McpClient,
    private val cloudId: String,
    private val defaultProject: String = "PROJ"
) : Skill {

    override val name = "atlassian-mcp"
    override val description = "Atlassian MCP (Jira, Confluence via MCP)"

    override fun canHandle(request: SkillRequest): Boolean {
        val msg = request.message.lowercase()
        return msg.startsWith("/atlassian") ||
               msg.startsWith("/jira-mcp") ||
               msg.startsWith("/confluence-mcp")
    }

    override suspend fun handle(request: SkillRequest): SkillResponse {
        val message = request.message

        return try {
            when {
                message.startsWith("/atlassian tools") -> handleTools()
                message.startsWith("/atlassian search ") -> handleSearch(message)
                message.startsWith("/atlassian create issue ") -> handleCreateIssue(message)
                message.startsWith("/atlassian create page ") -> handleCreatePage(message)
                message.startsWith("/atlassian summarize ") -> handleSummarize(message)
                message == "/atlassian help" -> help()
                message == "/atlassian" -> help()
                else -> help()
            }
        } catch (e: Exception) {
            SkillResponse(false, "Error: ${e.message}", skillName = name)
        }
    }

    private suspend fun handleTools(): SkillResponse {
        val result = mcpClient.listTools()
        return if (result.isSuccess) {
            val tools = result.getOrNull() ?: emptyList()
            val text = tools.joinToString("\n") { "${it.name}: ${it.description}" }
            SkillResponse(true, text, tools, name)
        } else {
            SkillResponse(false, "Failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleSearch(message: String): SkillResponse {
        val query = message.removePrefix("/atlassian search ").trim()
        if (query.isBlank()) {
            return SkillResponse(false, "Usage: /atlassian search <query>", name = name)
        }

        val result = mcpClient.callTool("jira_search", mapOf(
            "cloudId" to cloudId,
            "query" to query,
            "limit" to 10
        ))

        return if (result.isSuccess) {
            val json = result.getOrNull()?.toString() ?: ""
            SkillResponse(true, json.take(2000), name = name)
        } else {
            SkillResponse(false, "Search failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleCreateIssue(message: String): SkillResponse {
        val args = message.removePrefix("/atlassian create issue ").split("|", limit = 3)
        val title = args.getOrNull(0)?.trim() ?: ""
        val description = args.getOrNull(1)?.trim() ?: ""
        val projectKey = args.getOrNull(2)?.trim() ?: defaultProject

        if (title.isBlank()) {
            return SkillResponse(false, "Usage: /atlassian create issue <title> | [description] | [project]", name = name)
        }

        val result = mcpClient.callTool("jira_create_issue", mapOf(
            "cloudId" to cloudId,
            "title" to title,
            "description" to description,
            "projectId" to projectKey,
            "issueType" to "Task"
        ))

        return if (result.isSuccess) {
            SkillResponse(true, "Issue created: ${result.getOrNull()?.toString()}", name = name)
        } else {
            SkillResponse(false, "Failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleCreatePage(message: String): SkillResponse {
        val args = message.removePrefix("/atlassian create page ").split("|", limit = 3)
        val title = args.getOrNull(0)?.trim() ?: ""
        val content = args.getOrNull(1)?.trim() ?: ""
        val spaceId = args.getOrNull(2)?.trim() ?: " ~global"

        if (title.isBlank()) {
            return SkillResponse(false, "Usage: /atlassian create page <title> | [content] | [spaceId]", name = name)
        }

        val result = mcpClient.callTool("confluence_create_page", mapOf(
            "cloudId" to cloudId,
            "title" to title,
            "body" to content,
            "spaceId" to spaceId
        ))

        return if (result.isSuccess) {
            SkillResponse(true, "Page created: ${result.getOrNull()?.toString()}", name = name)
        } else {
            SkillResponse(false, "Failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleSummarize(message: String): SkillResponse {
        val query = message.removePrefix("/atlassian summarize ").trim()

        val result = mcpClient.callTool("confluence_summarize", mapOf(
            "cloudId" to cloudId,
            "query" to query
        ))

        return if (result.isSuccess) {
            SkillResponse(true, result.getOrNull()?.toString() ?: "No summary", name = name)
        } else {
            SkillResponse(false, "Failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private fun help(): SkillResponse {
        return SkillResponse(
            true,
            """
            |Atlassian MCP Skills:
            |  /atlassian tools              - List available MCP tools
            |  /atlassian search <query>   - Search Jira/Confluence
            |  /atlassian create issue <title> | [desc] | [proj] - Create Jira issue
            |  /atlassian create page <title> | [body] | [space] - Create Confluence page
            |  /atlassian summarize <query> - Summarize content
            |  /atlassian help           - Show this help
            |
            |Requires: Atlassian MCP server configured
            """.trimMargin(),
            name = name
        )
    }
}