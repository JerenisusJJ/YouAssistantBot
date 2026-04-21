package com.aiassistant.integration.gitlab

import com.aiassistant.skill.*

class GitLabSkill(
    private val client: GitLabClient,
    private val defaultProject: String = ""
) : Skill {

    override val name = "gitlab"
    override val description = "GitLab integration for MR, pipelines and projects"

    override fun canHandle(request: SkillRequest): Boolean {
        val msg = request.message.lowercase()
        return msg.contains("gitlab") ||
               msg.contains("merge") ||
               msg.contains("mr") ||
               msg.contains("pull request") ||
               msg.contains("pipeline") ||
               msg.contains("gitlab")
    }

    override suspend fun handle(request: SkillRequest): SkillResponse {
        val message = request.message

        return try {
            when {
                message.startsWith("/gitlab mr ") -> handleMRList(message)
                message.startsWith("/gitlab create mr ") -> handleMRCreate(message)
                message.startsWith("/gitlab merge ") -> handleMRMerge(message)
                message.startsWith("/gitlab pipeline ") -> handlePipeline(message)
                message.startsWith("/gitlab project ") -> handleProject(message)
                message.startsWith("/gitlab projects") -> handleProjects(message)
                message == "/gitlab help" -> help()
                message == "/gitlab" -> help()
                else -> handleNaturalLanguage(request)
            }
        } catch (e: Exception) {
            SkillResponse(false, "Error: ${e.message}", skillName = name)
        }
    }

    private suspend fun handleMRList(message: String): SkillResponse {
        val args = message.removePrefix("/gitlab mr ").trim()
        val projectId = args.ifBlank { defaultProject }
        if (projectId.isBlank()) {
            return SkillResponse(false, "Usage: /gitlab mr <project-id-or-path>", name = name)
        }

        val result = client.getMergeRequests(projectId)
        return if (result.isSuccess) {
            val mrs = result.getOrNull() ?: emptyList()
            if (mrs.isEmpty()) {
                SkillResponse(true, "No open MRs found", name = name)
            } else {
                val text = mrs.joinToString("\n") { "!${it.iid} ${it.title} (${it.state}) ${it.sourceBranch} -> ${it.targetBranch}" }
                SkillResponse(true, text, mrs, name)
            }
        } else {
            SkillResponse(false, "Error: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleMRCreate(message: String): SkillResponse {
        val args = message.removePrefix("/gitlab create mr ").split("|")
        if (args.size < 2) {
            return SkillResponse(false, "Usage: /gitlab create mr <title> | <source-branch> | [target-branch] | [project]", name = name)
        }

        val title = args[0].trim()
        val sourceBranch = args[1].trim()
        val targetBranch = args.getOrNull(2)?.trim() ?: "main"
        val projectId = args.getOrNull(3)?.trim() ?: defaultProject

        if (projectId.isBlank()) {
            return SkillResponse(false, "Specify project: /gitlab create mr <title> | <source> | <target> | <project>", name = name)
        }

        val result = client.createMergeRequest(projectId, title, sourceBranch, targetBranch)
        return if (result.isSuccess) {
            val mr = result.getOrNull()
            SkillResponse(true, "MR created: ${mr?.webUrl}", mr, name)
        } else {
            SkillResponse(false, "Failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleMRMerge(message: String): SkillResponse {
        val args = message.removePrefix("/gitlab merge ").split(" ")
        if (args.size < 2) {
            return SkillResponse(false, "Usage: /gitlab merge <project> <mr-iid>", name = name)
        }

        val projectId = args[0]
        val mrIid = args[1].removePrefix("!").toIntOrNull()

        if (mrIid == null) {
            return SkillResponse(false, "Invalid MR number: ${args[1]}", name = name)
        }

        val result = client.mergeMergeRequest(projectId, mrIid)
        return if (result.isSuccess) {
            SkillResponse(true, "MR !${mrIid} merged successfully", name = name)
        } else {
            SkillResponse(false, "Failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handlePipeline(message: String): SkillResponse {
        val args = message.removePrefix("/gitlab pipeline ").trim()
        val parts = args.split(" ")
        val projectId = parts.getOrNull(0) ?: defaultProject
        val ref = parts.getOrNull(1)

        if (projectId.isBlank()) {
            return SkillResponse(false, "Usage: /gitlab pipeline <project> [branch]", name = name)
        }

        val result = client.getPipelines(projectId, ref)
        return if (result.isSuccess) {
            val pipelines = result.getOrNull() ?: emptyList()
            if (pipelines.isEmpty()) {
                SkillResponse(true, "No pipelines found", name = name)
            } else {
                val text = pipelines.take(5).joinToString("\n") { "#${it.id} ${it.status} (${it.ref})" }
                SkillResponse(true, text, pipelines, name)
            }
        } else {
            SkillResponse(false, "Error: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleProject(message: String): SkillResponse {
        val search = message.removePrefix("/gitlab project ").trim()

        val result = client.getProjects(search.ifBlank { null })
        return if (result.isSuccess) {
            val projects = result.getOrNull() ?: emptyList()
            if (projects.isEmpty()) {
                SkillResponse(true, "No projects found", name = name)
            } else {
                val text = projects.take(10).joinToString("\n") { "${it.path} (id: ${it.id})" }
                SkillResponse(true, text, projects, name)
            }
        } else {
            SkillResponse(false, "Error: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleProjects(message: String): SkillResponse {
        return handleProject("/gitlab project")
    }

    private suspend fun handleNaturalLanguage(request: SkillRequest): SkillResponse {
        val msg = request.message.lowercase()

        return when {
            msg.contains("mr") || msg.contains("merge request") -> {
                handleMRList("/gitlab mr $defaultProject")
            }
            msg.contains("pipeline") -> {
                handlePipeline("/gitlab pipeline $defaultProject")
            }
            msg.contains("project") -> {
                handleProject("/gitlab project")
            }
            else -> help()
        }
    }

    private fun help(): SkillResponse {
        return SkillResponse(
            true,
            """
            |GitLab Skills:
            |  /gitlab mr <project>              - List open MRs
            |  /gitlab create mr <title> | <source> | [target] | [project] - Create MR
            |  /gitlab merge <project> <mr-iid> - Merge MR
            |  /gitlab pipeline <project> [branch] - Show pipelines
            |  /gitlab project [search]    - Search projects
            |  /gitlab help                - Show this help
            |
            |Examples:
            |  /gitlab mr my-project
            |  /gitlab create mr Fix bug | feature-branch | main | my-project
            |  покажи MRы
            """.trimMargin(),
            name = name
        )
    }
}