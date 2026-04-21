package com.aiassistant.integration.jira

import kotlinx.coroutines.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

data class JiraServerConfig(
    val baseUrl: String,
    val user: String,
    val password: String
)

data class JiraServerIssue(
    val id: String,
    val key: String,
    val summary: String,
    val description: String?,
    val status: String,
    val statusCategory: String,
    val assignee: String?,
    val priority: String?,
    val issueType: String,
    val created: String,
    val updated: String
)

class JiraServerClient(private val config: JiraServerConfig) {

    private val httpClient = HttpClient.newBuilder().build()
    private val auth: String = Base64.getEncoder()
        .encodeToString("${config.user}:${config.password}".toByteArray())
    private val baseUrl = config.baseUrl.trimEnd('/')

    private fun makeRequest(endpoint: String, method: String = "GET", body: String? = null): HttpResponse<String> {
        val url = "$baseUrl/rest/api/2/$endpoint"
        val builder = HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .header("Authorization", "Basic $auth")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        when (method) {
            "GET" -> builder.GET()
            "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body ?: ""))
            "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body ?: ""))
            "DELETE" -> builder.DELETE()
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    suspend fun searchIssues(jql: String, maxResults: Int = 50): Result<List<JiraServerIssue>> = withContext(Dispatchers.IO) {
        try {
            val encodedJql = java.net.URLEncoder.encode(jql, "UTF-8")
            val response = makeRequest("search?jql=$encodedJql&maxResults=$maxResults")

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

    suspend fun getIssue(issueKey: String): Result<JiraServerIssue> = withContext(Dispatchers.IO) {
        try {
            val response = makeRequest("issue/$issueKey")

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

    suspend fun createIssue(projectKey: String, summary: String, description: String, issueType: String = "Task"): Result<JiraServerIssue> = withContext(Dispatchers.IO) {
        try {
            val body = """
                {
                    "fields": {
                        "project": {"key": "$projectKey"},
                        "summary": "$summary",
                        "description": {
                            "type": "doc",
                            "version": 1,
                            "content": [{
                                "content": [{"text": "$description"}],
                                "type": "paragraph"
                            }]
                        },
                        "issuetype": {"name": "$issueType"}
                    }
                }
            """.trimIndent()

            val response = makeRequest("issue", "POST", body)

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

    suspend fun updateIssue(issueKey: String, fields: Map<String, Any>): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val fieldsJson = StringBuilder("{")
            fields.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) fieldsJson.append(",")
                fieldsJson.append("\"$key\":")
                when (value) {
                    is String -> fieldsJson.append("\"$value\"")
                    is Number -> fieldsJson.append(value)
                    else -> fieldsJson.append("\"$value\"")
                }
            }
            fieldsJson.append("}")

            val response = makeRequest("issue/$issueKey", "PUT", fieldsJson.toString())

            if (response.statusCode() == 204) {
                Result.success(true)
            } else {
                Result.failure(Exception("Update failed: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun transitionIssue(issueKey: String, transitionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = """{"transition":{"id":"$transitionId"}}"""
            val response = makeRequest("issue/$issueKey/transitions", "POST", body)

            if (response.statusCode() == 204) {
                Result.success(true)
            } else {
                Result.failure(Exception("Transition failed: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTransitions(issueKey: String): Result<List<JiraTransition>> = withContext(Dispatchers.IO) {
        try {
            val response = makeRequest("issue/$issueKey/transitions")

            if (response.statusCode() == 200) {
                val transitions = parseTransitions(response.body())
                Result.success(transitions)
            } else {
                Result.failure(Exception("Failed: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProjects(): Result<List<JiraProject>> = withContext(Dispatchers.IO) {
        try {
            val response = makeRequest("project")

            if (response.statusCode() == 200) {
                val projects = parseProjects(response.body())
                Result.success(projects)
            } else {
                Result.failure(Exception("Failed: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseIssues(json: String): List<JiraServerIssue> {
        val issues = mutableListOf<JiraServerIssue>()
        val issuesArray = json.split("\"issues\":[").getOrNull(1)?.split("]")?.getOrNull(0) ?: ""

        val regex = """"key":"([^"]+)".*?"summary":"([^"]+)".*?"name":"([^"]+)".*?"name":"([^"]+)"""".toRegex()
        regex.findAll(issuesArray).forEach { match ->
            issues.add(JiraServerIssue(
                id = "",
                key = match.groupValues[1],
                summary = match.groupValues[2],
                description = null,
                status = "",
                statusCategory = "",
                assignee = null,
                priority = null,
                issueType = match.groupValues[3],
                created = "",
                updated = ""
            ))
        }

        return issues
    }

    private fun parseIssue(json: String): JiraServerIssue {
        return JiraServerIssue(
            id = json.split("\"id\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: "",
            key = json.split("\"key\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: "",
            summary = json.split("\"summary\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: "",
            description = json.split("\"description\":").getOrNull(1)?.take(200),
            status = json.split("\"name\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: "",
            statusCategory = "",
            assignee = json.split("\"displayName\":\"").getOrNull(1)?.split("\"")?.getOrNull(0),
            priority = json.split("\"name\":\"").getOrNull(3)?.split("\"")?.getOrNull(0),
            issueType = json.split("\"name\":\"").getOrNull(5)?.split("\"")?.getOrNull(0) ?: "Task",
            created = json.split("\"created\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: "",
            updated = json.split("\"updated\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: ""
        )
    }

    private fun parseTransitions(json: String): List<JiraTransition> {
        val transitions = mutableListOf<JiraTransition>()
        val regex = """"id":"([^"]+)".*?"name":"([^"]+)".*?"to":{"name":"([^"]+)"""".toRegex()

        regex.findAll(json).forEach { match ->
            transitions.add(JiraTransition(
                id = match.groupValues[1],
                name = match.groupValues[2],
                toStatus = match.groupValues[3]
            ))
        }

        return transitions
    }

    private fun parseProjects(json: String): List<JiraProject> {
        val projects = mutableListOf<JiraProject>()
        val regex = """"key":"([^"]+)".*?"name":"([^"]+)"""".toRegex()

        regex.findAll(json).forEach { match ->
            projects.add(JiraProject(
                key = match.groupValues[1],
                name = match.groupValues[2]
            ))
        }

        return projects
    }

    private fun extractKey(json: String): String {
        return json.split("\"key\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: ""
    }
}

data class JiraTransition(
    val id: String,
    val name: String,
    val toStatus: String
)

data class JiraProject(
    val key: String,
    val name: String
)