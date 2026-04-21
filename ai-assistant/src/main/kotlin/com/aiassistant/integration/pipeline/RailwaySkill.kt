package com.aiassistant.integration.pipeline

import com.aiassistant.skill.*
import kotlinx.coroutines.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class RailwayConfig(
    val token: String,
    val projectId: String? = null
)

data class RailwayDeployment(
    val id: String,
    val status: String,
    val createdAt: String,
    val serviceName: String
)

class RailwayClient(private val config: RailwayConfig) {

    private val httpClient = HttpClient.newBuilder().build()
    private val baseUrl = "https://railway.com/api/v2"

    private fun makeRequest(endpoint: String, method: String = "GET", body: String? = null): HttpResponse<String> {
        val url = "$baseUrl/$endpoint"
        val builder = HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .header("Authorization", "Bearer ${config.token}")
            .header("Content-Type", "application/json")

        when (method) {
            "GET" -> builder.GET()
            "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body ?: ""))
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    suspend fun getDeployments(): Result<List<RailwayDeployment>> = withContext(Dispatchers.IO) {
        try {
            val response = makeRequest("deployments?limit=10")

            if (response.statusCode() == 200) {
                val deployments = parseDeployments(response.body())
                Result.success(deployments)
            } else {
                Result.failure(Exception("API error: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProjectStatus(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = makeRequest("projects")

            if (response.statusCode() == 200) {
                Result.success(response.body().take(500))
            } else {
                Result.failure(Exception("Error: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDeployments(json: String): List<RailwayDeployment> {
        val deployments = mutableListOf<RailwayDeployment>()
        val regex = """"id":"([^"]+)".*?"status":"([^"]+)".*?"createdAt":"([^"]+)"""".toRegex()

        regex.findAll(json).forEach { match ->
            deployments.add(RailwayDeployment(
                id = match.groupValues[1],
                status = match.groupValues[2],
                createdAt = match.groupValues[3],
                serviceName = ""
            ))
        }

        return deployments
    }
}

class RailwaySkill(
    private val client: RailwayClient
) : Skill {

    override val name = "railway"
    override val description = "Railway deployment management"

    override fun canHandle(request: SkillRequest): Boolean {
        val msg = request.message.lowercase()
        return msg.startsWith("/railway") ||
               msg.contains("deploy") ||
               msg.contains("деплой")
    }

    override suspend fun handle(request: SkillRequest): SkillResponse {
        val message = request.message

        return try {
            when {
                message.startsWith("/railway status") -> handleStatus()
                message.startsWith("/railway deployments") -> handleDeployments()
                message == "/railway help" -> help()
                else -> help()
            }
        } catch (e: Exception) {
            SkillResponse(false, "Error: ${e.message}", skillName = name)
        }
    }

    private suspend fun handleStatus(): SkillResponse {
        val result = client.getProjectStatus()
        return if (result.isSuccess) {
            val info = result.getOrNull() ?: "No data"
            val lines = info.split("\n").take(10).joinToString("\n")
            SkillResponse(true, "Railway Status:\n$lines", name = name)
        } else {
            SkillResponse(false, "Failed: ${result.exceptionOrNull()?.message}", name = name)
        }
    }

    private suspend fun handleDeployments(): SkillResponse {
        val result = client.getDeployments()
        return if (result.isSuccess) {
            val deployments = result.getOrNull() ?: emptyList()
            val text = deployments.take(5).joinToString("\n") { "${it.id.take(8)}... ${it.status} ${it.createdAt}" }
            SkillResponse(true, text, deployments, name)
        } else {
            SkillResponse(false, "Failed", name = name)
        }
    }

    private fun help(): SkillResponse {
        return SkillResponse(
            true,
            """
            |Railway Skills:
            |  /railway status          - Project status
            |  /railway deployments  - Recent deployments
            |  /railway help         - Show this help
            |
            |Note: Use Railway CLI or dashboard for deployments
            """.trimMargin(),
            name = name
        )
    }
}