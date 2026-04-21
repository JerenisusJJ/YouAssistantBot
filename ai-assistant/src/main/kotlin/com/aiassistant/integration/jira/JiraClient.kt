package com.aiassistant.integration.jira

import kotlinx.coroutines.*
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class JiraConfig(
    val baseUrl: String,
    val user: String,
    val token: String
)

data class JiraIssue(
    val key: String,
    val summary: String,
    val description: String?,
    val status: String,
    val assignee: String?,
    val priority: String?
)

class JiraClient(private val config: JiraConfig) {

    private val httpClient = HttpClient.newBuilder().build()
    private val auth: String = java.util.Base64.getEncoder()
        .encodeToString("${config.user}:${config.token}".toByteArray())

    private fun makeRequest(method: String, endpoint: String, body: String? = null): HttpResponse<String> {
        val url = "${config.baseUrl}/rest/api/3/$endpoint"
        val builder = HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .header("Authorization", "Basic $auth")
            .header("Content-Type", "application/json")

        when (method) {
            "GET" -> builder.GET()
            "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body ?: ""))
            "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body ?: ""))
            "DELETE" -> builder.DELETE()
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    suspend fun searchIssues(jql: String, maxResults: Int = 50): Result<List<JiraIssue>> = withContext(Dispatchers.IO) {
        try {
            val encodedJql = URLEncoder.encode(jql, "UTF-8")
            val response = makeRequest("GET", "search?jql=$encodedJql&maxResults=$maxResults")

            if (response.statusCode() == 200) {
                val issues = parseIssues(response.body())
                Result.success(issues)
            } else {
                Result.failure(Exception("Jira API error: ${response.statusCode()} - ${response.body()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getIssue(issueKey: String): Result<JiraIssue> = withContext(Dispatchers.IO) {
        try {
            val response = makeRequest("GET", "issue/$issueKey")

            if (response.statusCode() == 200) {
                val issue = parseIssue(response.body())
                Result.success(issue)
            } else {
                Result.failure(Exception("Issue not found: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createIssue(projectKey: String, summary: String, description: String, issueType: String = "Task"): Result<JiraIssue> = withContext(Dispatchers.IO) {
        try {
            val body = """
                {
                    "fields": {
                        "project": {"key": "$projectKey"},
                        "summary": "$summary",
                        "description": {"type": "doc", "version": 1, "content": [{"type": "paragraph", "content": [{"type": "text", "text": "$description"}]}]},
                        "issuetype": {"name": "$issueType"}
                    }
                }
            """.trimIndent()

            val response = makeRequest("POST", "issue", body)

            if (response.statusCode() == 201) {
                val key = extractKey(response.body())
                getIssue(key)
            } else {
                Result.failure(Exception("Failed to create issue: ${response.statusCode()} - ${response.body()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateIssueStatus(issueKey: String, transitionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = makeRequest("POST", "issue/$issueKey/transitions", """{"transition":{"id":"$transitionId"}""")

            if (response.statusCode() == 204) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to transition: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseIssues(json: String): List<JiraIssue> {
        val issues = mutableListOf<JiraIssue>()
        val issueRegex = "\"key\":\"([^\"]+)\".*?\"summary\":\"([^\"]+)\"".toRegex()

        issueRegex.findAll(json).forEach { match ->
            val key = match.groupValues[1]
            val summary = match.groupValues[2]
            issues.add(JiraIssue(key, summary, null, "Unknown", null, null))
        }

        return issues
    }

    private fun parseIssue(json: String): JiraIssue {
        val key = json.split("\"key\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: ""
        val summary = json.split("\"summary\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: ""
        val status = json.split("\"name\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: ""
        val description = json.split("\"description\":").getOrNull(1)?.take(100)

        return JiraIssue(key, summary, description, status, null, null)
    }

    private fun extractKey(json: String): String {
        return json.split("\"key\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: ""
    }
}
