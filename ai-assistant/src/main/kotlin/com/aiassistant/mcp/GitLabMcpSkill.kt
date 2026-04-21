package com.aiassistant.mcp

import com.aiassistant.skill.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

class GitLabMcpSkill(
    private val mcpClient: McpClient
) : Skill {

    override val name = "gitlab-mcp"
    override val description = "GitLab MCP integration"

    override fun canHandle(request: SkillRequest): Boolean {
        val msg = request.message.lowercase()
        return msg.startsWith("/gitlab-mcp") ||
               msg.contains("merge request") && msg.contains("mcp")
    }

    override suspend fun handle(request: SkillRequest): SkillResponse {
        val message = request.message

        return try {
            when {
                message.startsWith("/gitlab-mcp search ") -> handleSearch(message)
                message.startsWith("/gitlab-mcp mr ") -> handleMR(message)
                message.startsWith("/gitlab-mcp pipelines") -> handlePipelines(message)
                message == "/gitlab-mcp help" -> help()
                message == "/gitlab-mcp" -> help()
                else -> help()
            }
        } catch (e: Exception) {
            SkillResponse(false, "Error: ${e.message}", skillName = name)
        }
    }

    private suspend fun handleSearch(message: String): SkillResponse {
        val query = message.removePrefix("/gitlab-mcp search ").trim()

        val result = mcpClient.callTool("gitlab_search", mapOf(
            "query" to query,
            "scope" to "blobs"
        ))

        return if (result.isSuccess) {
            SkillResponse(true, result.getOrNull()?.toString()?.take(2000) ?: "No results", name = name)
        } else {
            SkillResponse(false, "Search failed", name = name)
        }
    }

    private suspend fun handleMR(message: String): SkillResponse {
        val args = message.removePrefix("/gitlab-mcp mr ").trim().split(" ")
        val projectPath = args.getOrNull(0) ?: ""
        val state = args.getOrNull(1) ?: "opened"

        val result = mcpClient.callTool("gitlab_list_merge_requests", mapOf(
            "project" to projectPath,
            "state" to state,
            "limit" to 10
        ))

        return if (result.isSuccess) {
            SkillResponse(true, result.getOrNull()?.toString()?.take(2000) ?: "No MRs", name = name)
        } else {
            SkillResponse(false, "Failed", name = name)
        }
    }

    private suspend fun handlePipelines(message: String): SkillResponse {
        val project = message.removePrefix("/gitlab-mcp pipelines ").trim()

        val result = mcpClient.callTool("gitlab_list_pipelines", mapOf(
            "project" to project,
            "limit" to 5
        ))

        return if (result.isSuccess) {
            SkillResponse(true, result.getOrNull()?.toString()?.take(2000) ?: "No pipelines", name = name)
        } else {
            SkillResponse(false, "Failed", name = name)
        }
    }

    private fun help(): SkillResponse {
        return SkillResponse(
            true,
            """
            |GitLab MCP Skills:
            |  /gitlab-mcp search <query>    - Search code
            |  /gitlab-mcp mr <project>    - List MRs
            |  /gitlab-mcp pipelines <project> - List pipelines
            |  /gitlab-mcp help            - Show this help
            """.trimMargin(),
            name = name
        )
    }
}