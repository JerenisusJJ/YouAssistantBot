package com.aiassistant.integration.mattermost

import kotlinx.coroutines.*
import kotlinx.coroutines.http.client.*
import java.net.URLEncoder

data class MattermostConfig(
    val baseUrl: String,
    val botToken: String,
    val teamName: String
)

data class MattermostMessage(
    val channelId: String,
    val message: String,
    val userId: String? = null
)

class MattermostClient(private val config: MattermostConfig) {

    private val httpClient = HttpClient()

    suspend fun sendMessage(channelId: String, text: String): Result<MattermostMessage> = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.post("${config.baseUrl}/api/v4/posts") {
                header("Authorization", "Bearer ${config.botToken}")
                header("Content-Type", "application/json")
                setBody("""
                    {
                        "channel_id": "$channelId",
                        "message": "$text"
                    }
                """.trimIndent())
            }

            if (response.status == 201) {
                Result.success(MattermostMessage(channelId, text))
            } else {
                Result.failure(Exception("Failed to send: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChannelId(teamId: String, channelName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val encodedName = URLEncoder.encode(channelName, "UTF-8")
            val response = httpClient.get("${config.baseUrl}/api/v4/teams/$teamId/channels/name/$encodedName") {
                header("Authorization", "Bearer ${config.botToken}")
            }

            if (response.status == 200) {
                val channelId = extractChannelId(response.body)
                Result.success(channelId)
            } else {
                Result.failure(Exception("Channel not found: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyUserId(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.get("${config.baseUrl}/api/v4/users/me") {
                header("Authorization", "Bearer ${config.botToken}")
            }

            if (response.status == 200) {
                Result.success(extractUserId(response.body))
            } else {
                Result.failure(Exception("Failed to get user: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listTeams(): Result<List<Team>> = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.get("${config.baseUrl}/api/v4/users/me/teams") {
                header("Authorization", "Bearer ${config.botToken}")
            }

            if (response.status == 200) {
                Result.success(parseTeams(response.body))
            } else {
                Result.failure(Exception("Failed to list teams: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractChannelId(json: String): String {
        return json.split("\"id\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: ""
    }

    private fun extractUserId(json: String): String {
        return json.split("\"id\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: ""
    }

    private fun parseTeams(json: String): List<Team> {
        val teams = mutableListOf<Team>()
        val teamRegex = "\"id\":\"([^\"]+)\".*?\"name\":\"([^\"]+)\"".toRegex()
        teamRegex.findAll(json).forEach { match ->
            teams.add(Team(match.groupValues[1], match.groupValues[2]))
        }
        return teams
    }

    fun close() {
        httpClient.close()
    }
}

data class Team(val id: String, val name: String)