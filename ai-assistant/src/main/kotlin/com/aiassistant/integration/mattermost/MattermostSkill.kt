package com.aiassistant.integration.mattermost

import com.aiassistant.skill.*

class MattermostSkill(
    private val client: MattermostClient,
    private val defaultChannel: String = "personal"
) : Skill {

    override val name = "mattermost"
    override val description = "Mattermost integration for messaging"

    override fun canHandle(request: SkillRequest): Boolean {
        val msg = request.message.lowercase()
        return msg.startsWith("/msg") ||
               msg.startsWith("send to") ||
               msg.startsWith("напиши") ||
               msg.contains("mattermost") ||
               msg.contains("канал")
    }

    override suspend fun handle(request: SkillRequest): SkillResponse {
        val message = request.message

        return try {
            when {
                message.startsWith("/msg ") -> handleDirectMessage(message, request)
                message.startsWith("/channel ") -> handleChannelMessage(message, request)
                message.startsWith("/teams") -> handleTeams(request)
                message.startsWith("/channels") -> handleChannels(request)
                message == "/help" -> help()
                else -> help()
            }
        } catch (e: Exception) {
            SkillResponse(false, "Error: ${e.message}", skillName = name)
        }
    }

    private suspend fun handleDirectMessage(message: String, request: SkillRequest): SkillResponse {
        val parts = message.removePrefix("/msg ").split(" ", limit = 2)
        val channelOrUser = parts.getOrNull(0) ?: return SkillResponse(false, "Usage: /msg <user|channel> <text>", name = name)
        val text = parts.getOrNull(1) ?: return SkillResponse(false, "Usage: /msg <user|channel> <text>", name = name)

        val channelResult = when {
            channelOrUser.startsWith("@") -> {
                val userIdResult = client.getMyUserId()
                if (userIdResult.isFailure) {
                    return SkillResponse(false, "Cannot get user ID", name = name)
                }
                channelOrUser.removePrefix("@")
            }
            else -> channelOrUser
        }

        val teamListResult = client.listTeams()
        if (teamListResult.isFailure) {
            return SkillResponse(false, "Cannot list teams", name = name)
        }

        val team = teamListResult.getOrNull()?.firstOrNull()
            ?: return SkillResponse(false, "No teams found", name = name)

        val channelIdResult = client.getChannelId(team.id, channelResult)
        if (channelIdResult.isFailure) {
            return SkillResponse(false, "Channel not found: $channelOrUser", name = name)
        }

        val sendResult = client.sendMessage(channelIdResult.getOrThrow(), text)
        return if (sendResult.isSuccess) {
            SkillResponse(true, "Message sent to $channelOrUser", name = name)
        } else {
            SkillResponse(false, "Failed to send: ${sendResult.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleChannelMessage(message: String, request: SkillRequest): SkillResponse {
        return handleDirectMessage(message.replace("/channel ", "/msg "), request)
    }

    private suspend fun handleTeams(request: SkillRequest): SkillResponse {
        val result = client.listTeams()
        return if (result.isSuccess) {
            val teams = result.getOrNull() ?: emptyList()
            val text = teams.joinToString("\n") { "${it.name} (${it.id})" }
            SkillResponse(true, if (text.isBlank()) "No teams" else text, teams, name)
        } else {
            SkillResponse(false, "Error: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleChannels(request: SkillRequest): SkillResponse {
        return handleTeams(request)
    }

    private fun help(): SkillResponse {
        return SkillResponse(
            true,
            """
            |Mattermost Skills:
            |  /msg <channel|user> <text>  - Send message
            |  /channel <name> <text>      - Send to channel
            |  /teams                     - List teams
            |  /help                     - Show this help
            """.trimMargin(),
            name = name
        )
    }
}