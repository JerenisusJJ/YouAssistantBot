package com.aiassistant.integration.confluence

import com.aiassistant.skill.*

class ConfluenceSkill(
    private val client: ConfluenceClient,
    private val defaultSpace: String = "SPACE"
) : Skill {

    override val name = "confluence"
    override val description = "Confluence integration for documentation"

    override fun canHandle(request: SkillRequest): Boolean {
        val msg = request.message.lowercase()
        return msg.contains("confluence") ||
               msg.contains("документ") ||
               msg.contains("дока") ||
               msg.contains("docs") ||
               msg.contains("wiki") ||
               msg.startsWith("/docs")
    }

    override suspend fun handle(request: SkillRequest): SkillResponse {
        val message = request.message

        return try {
            when {
                message.startsWith("/docs search ") -> handleSearch(message)
                message.startsWith("/docs get ") -> handleGet(message)
                message.startsWith("/docs create ") -> handleCreate(message)
                message.startsWith("/docs spaces") -> handleSpaces(message)
                message == "/docs help" -> help()
                message == "/docs" -> help()
                else -> handleNaturalLanguage(request)
            }
        } catch (e: Exception) {
            SkillResponse(false, "Error: ${e.message}", skillName = name)
        }
    }

    private suspend fun handleSearch(message: String): SkillResponse {
        val query = message.removePrefix("/docs search ").trim()
        if (query.isBlank()) {
            return SkillResponse(false, "Usage: /docs search <query>", name = name)
        }

        val result = client.search(query)
        return if (result.isSuccess) {
            val pages = result.getOrNull() ?: emptyList()
            if (pages.isEmpty()) {
                SkillResponse(true, "No pages found", name = name)
            } else {
                val text = pages.joinToString("\n") { "${it.title} (${it.space})" }
                SkillResponse(true, text, pages, name)
            }
        } else {
            SkillResponse(false, "Search failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleGet(message: String): SkillResponse {
        val args = message.removePrefix("/docs get ").trim()
        if (args.isBlank()) {
            return SkillResponse(false, "Usage: /docs get <page-id-or-title>", name = name)
        }

        val result = if (args.startsWith("~")) {
            val pageId = args.removePrefix("~")
            client.getPage(pageId)
        } else {
            client.getPageByTitle(defaultSpace, args)
        }

        return if (result.isSuccess) {
            val page = result.getOrNull()
            val text = """
                |${page?.title}
                |Space: ${page?.space}
                |Status: ${page?.status}
                |---
                |${page?.body?.take(500) ?: "No content"}
            """.trimMargin()

            SkillResponse(true, text, page, name)
        } else {
            SkillResponse(false, "Page not found", name = name)
        }
    }

    private suspend fun handleCreate(message: String): SkillResponse {
        val args = message.removePrefix("/docs create ").split("|", limit = 3)
        if (args.size < 2) {
            return SkillResponse(false, "Usage: /docs create <title> | <content> | [space]", name = name)
        }

        val title = args[0].trim()
        val content = args[1].trim()
        val space = args.getOrNull(2)?.trim() ?: defaultSpace

        val result = client.createPage(space, title, content)
        return if (result.isSuccess) {
            val page = result.getOrNull()
            SkillResponse(true, "Page created: ${page?.title}", page, name)
        } else {
            SkillResponse(false, "Failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleSpaces(message: String): SkillResponse {
        val result = client.getSpaces()
        return if (result.isSuccess) {
            val spaces = result.getOrNull() ?: emptyList()
            val text = spaces.joinToString("\n") { "${it.key}: ${it.name}" }
            SkillResponse(true, text, spaces, name)
        } else {
            SkillResponse(false, "Failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleNaturalLanguage(request: SkillRequest): SkillResponse {
        val msg = request.message.lowercase()

        return when {
            msg.contains("найди") || msg.contains("search") -> {
                val query = msg.replace(Regex("найди|search|документ|confluence"), "").trim()
                handleSearch("/docs search $query")
            }
            msg.contains("создай") || msg.contains("create") -> {
                val args = msg.replace(Regex("создай|create|документ|confluence"), "").trim()
                handleCreate("/docs create $args | Content")
            }
            else -> help()
        }
    }

    private fun help(): SkillResponse {
        return SkillResponse(
            true,
            """
            |Confluence Skills:
            |  /docs search <query>    - Search pages
            |  /docs get <title>      - Get page by title
            |  /docs get ~<pageId>   - Get page by ID
            |  /docs create <title> | <content> | [space] - Create page
            |  /docs spaces          - List spaces
            |  /docs help           - Show this help
            |
            |Examples:
            |  /docs search тестирование
            |  /docs get Инструкция
            |  найди документ про API
            """.trimMargin(),
            name = name
        )
    }
}