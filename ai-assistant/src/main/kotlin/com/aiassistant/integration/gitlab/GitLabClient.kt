package com.aiassistant.integration.gitlab

import kotlinx.coroutines.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class GitLabConfig(
    val baseUrl: String,
    val token: String
)

data class GitLabMR(
    val iid: Int,
    val title: String,
    val state: String,
    val sourceBranch: String,
    val targetBranch: String,
    val author: String,
    val webUrl: String
)

data class GitLabProject(
    val id: Int,
    val name: String,
    val path: String,
    val webUrl: String
)

data class GitLabPipeline(
    val id: Int,
    val status: String,
    val ref: String,
    val webUrl: String
)

class GitLabClient(private val config: GitLabConfig) {

    private val httpClient = HttpClient.newBuilder().build()
    private val baseUrl = config.baseUrl.trimEnd('/')

    private fun makeRequest(endpoint: String, method: String = "GET", body: String? = null): HttpResponse<String> {
        val url = "$baseUrl/api/v4/$endpoint"
        val builder = HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .header("PRIVATE-TOKEN", config.token)
            .header("Content-Type", "application/json")

        when (method) {
            "GET" -> builder.GET()
            "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body ?: ""))
            "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body ?: ""))
            "DELETE" -> builder.DELETE()
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    suspend fun getMergeRequests(projectId: String, state: String = "opened"): Result<List<GitLabMR>> = withContext(Dispatchers.IO) {
        try {
            val response = makeRequest("projects/$projectId/merge_requests?state=$state")

            if (response.statusCode() == 200) {
                val mrs = parseMergeRequests(response.body())
                Result.success(mrs)
            } else {
                Result.failure(Exception("GitLab API error: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMergeRequest(projectId: String, mrIid: Int): Result<GitLabMR> = withContext(Dispatchers.IO) {
        try {
            val response = makeRequest("projects/$projectId/merge_requests/$mrIid")

            if (response.statusCode() == 200) {
                val mr = parseSingleMR(response.body())
                Result.success(mr)
            } else {
                Result.failure(Exception("MR not found: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createMergeRequest(projectId: String, title: String, sourceBranch: String, targetBranch: String = "main"): Result<GitLabMR> = withContext(Dispatchers.IO) {
        try {
            val body = """
                {
                    "title": "$title",
                    "source_branch": "$sourceBranch",
                    "target_branch": "$targetBranch"
                }
            """.trimIndent()

            val response = makeRequest("projects/$projectId/merge_requests", "POST", body)

            if (response.statusCode() == 201) {
                val mr = parseSingleMR(response.body())
                Result.success(mr)
            } else {
                Result.failure(Exception("Failed to create MR: ${response.statusCode()} - ${response.body()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun mergeMergeRequest(projectId: String, mrIid: Int, message: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = message?.let { """{"merge_commit_message": "$it"}""" } ?: "{}"
            val response = makeRequest("projects/$projectId/merge_requests/$mrIid/merge", "PUT", body)

            if (response.statusCode() == 200) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to merge: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPipelines(projectId: String, ref: String? = null): Result<List<GitLabPipeline>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = if (ref != null) {
                "projects/$projectId/pipelines?ref=$ref"
            } else {
                "projects/$projectId/pipelines"
            }

            val response = makeRequest(endpoint)

            if (response.statusCode() == 200) {
                val pipelines = parsePipelines(response.body())
                Result.success(pipelines)
            } else {
                Result.failure(Exception("Failed to get pipelines: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProjects(search: String? = null): Result<List<GitLabProject>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = if (search != null) {
                "projects?search=$search"
            } else {
                "projects?membership=true"
            }

            val response = makeRequest(endpoint)

            if (response.statusCode() == 200) {
                val projects = parseProjects(response.body())
                Result.success(projects)
            } else {
                Result.failure(Exception("Failed to get projects: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseMergeRequests(json: String): List<GitLabMR> {
        val mrs = mutableListOf<GitLabMR>()
        val regex = """"iid":(\d+).*?"title":"([^"]+)".*?"state":"([^"]+)".*?"source_branch":"([^"]+)".*?"target_branch":"([^"]+)".*?"username":"([^"]+)"""".toRegex()

        regex.findAll(json).forEach { match ->
            mrs.add(GitLabMR(
                iid = match.groupValues[1].toInt(),
                title = match.groupValues[2],
                state = match.groupValues[3],
                sourceBranch = match.groupValues[4],
                targetBranch = match.groupValues[5],
                author = match.groupValues[6],
                webUrl = ""
            ))
        }

        return mrs
    }

    private fun parseSingleMR(json: String): GitLabMR {
        return GitLabMR(
            iid = json.split("\"iid\":").getOrNull(1)?.split(",").getOrNull(0)?.toIntOrNull() ?: 0,
            title = json.split("\"title\":\"").getOrNull(1)?.split("\"").getOrNull(0) ?: "",
            state = json.split("\"state\":\"").getOrNull(1)?.split("\"").getOrNull(0) ?: "",
            sourceBranch = json.split("\"source_branch\":\"").getOrNull(1)?.split("\"").getOrNull(0) ?: "",
            targetBranch = json.split("\"target_branch\":\"").getOrNull(1)?.split("\"").getOrNull(0) ?: "",
            author = json.split("\"username\":\"").getOrNull(1)?.split("\"").getOrNull(0) ?: "",
            webUrl = json.split("\"web_url\":\"").getOrNull(1)?.split("\"").getOrNull(0) ?: ""
        )
    }

    private fun parsePipelines(json: String): List<GitLabPipeline> {
        val pipelines = mutableListOf<GitLabPipeline>()
        val regex = """"id":(\d+).*?"status":"([^"]+)".*?"ref":"([^"]+)"""".toRegex()

        regex.findAll(json).forEach { match ->
            pipelines.add(GitLabPipeline(
                id = match.groupValues[1].toInt(),
                status = match.groupValues[2],
                ref = match.groupValues[3],
                webUrl = ""
            ))
        }

        return pipelines
    }

    private fun parseProjects(json: String): List<GitLabProject> {
        val projects = mutableListOf<GitLabProject>()
        val regex = """"id":(\d+).*?"name":"([^"]+)".*?"path":"([^"]+)"""".toRegex()

        regex.findAll(json).forEach { match ->
            projects.add(GitLabProject(
                id = match.groupValues[1].toInt(),
                name = match.groupValues[2],
                path = match.groupValues[3],
                webUrl = ""
            ))
        }

        return projects
    }
}