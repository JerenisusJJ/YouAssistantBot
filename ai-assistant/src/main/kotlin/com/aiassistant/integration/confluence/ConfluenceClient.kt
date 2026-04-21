package com.aiassistant.integration.confluence

import kotlinx.coroutines.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

data class ConfluenceConfig(
    val baseUrl: String,
    val user: String,
    val token: String
)

data class ConfluencePage(
    val id: String,
    val title: String,
    val space: String,
    val type: String,
    val status: String,
    val body: String?,
    val url: String
)

class ConfluenceClient(private val config: ConfluenceConfig) {

    private val httpClient = HttpClient.newBuilder().build()
    private val auth: String = Base64.getEncoder()
        .encodeToString("${config.user}:${config.token}".toByteArray())
    private val baseUrl = config.baseUrl.trimEnd('/')

    private fun makeRequest(endpoint: String, method: String = "GET", body: String? = null): HttpResponse<String> {
        val url = "$baseUrl/wiki/api/v2/$endpoint"
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

    suspend fun search(cql: String, limit: Int = 10): Result<List<ConfluencePage>> = withContext(Dispatchers.IO) {
        try {
            val response = makeRequest("pages?cql=$cql&limit=$limit")

            if (response.statusCode() == 200) {
                val pages = parsePages(response.body())
                Result.success(pages)
            } else {
                Result.failure(Exception("Confluence API error: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPage(pageId: String): Result<ConfluencePage> = withContext(Dispatchers.IO) {
        try {
            val response = makeRequest("pages/$pageId")

            if (response.statusCode() == 200) {
                val page = parsePage(response.body())
                Result.success(page)
            } else {
                Result.failure(Exception("Page not found: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPageByTitle(spaceKey: String, title: String): Result<ConfluencePage> = withContext(Dispatchers.IO) {
        try {
            val cql = "space.key=$spaceKey AND title=$title"
            val response = makeRequest("pages?cql=$cql&limit=1")

            if (response.statusCode() == 200) {
                val pages = parsePages(response.body())
                if (pages.isNotEmpty()) {
                    Result.success(pages.first())
                } else {
                    Result.failure(Exception("Page not found"))
                }
            } else {
                Result.failure(Exception("API error: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPage(spaceKey: String, title: String, content: String, parentId: String? = null): Result<ConfluencePage> = withContext(Dispatchers.IO) {
        try {
            val body = buildString {
                append("""{"spaceId": "$spaceKey","title": "$title",""")
                append(""status": "current","body": {"representation": "storage","value": "$content"}""")
                parentId?.let { append(",""parentId": "$it""") }
                append("}")
            }

            val response = makeRequest("pages", "POST", body)

            if (response.statusCode() == 200) {
                val page = parsePage(response.body())
                Result.success(page)
            } else {
                Result.failure(Exception("Failed to create page: ${response.statusCode()} - ${response.body()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePage(pageId: String, title: String, content: String): Result<ConfluencePage> = withContext(Dispatchers.IO) {
        try {
            val body = """
                {"id": "$pageId","title": "$title","body": {"representation": "storage","value": "$content"},"status": "current"}
            """.trimIndent()

            val response = makeRequest("pages/$pageId", "PUT", body)

            if (response.statusCode() == 200) {
                val page = parsePage(response.body())
                Result.success(page)
            } else {
                Result.failure(Exception("Failed to update: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSpaces(): Result<List<ConfluenceSpace>> = withContext(Dispatchers.IO) {
        try {
            val response = makeRequest("spaces?limit=50")

            if (response.statusCode() == 200) {
                val spaces = parseSpaces(response.body())
                Result.success(spaces)
            } else {
                Result.failure(Exception("Failed: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePages(json: String): List<ConfluencePage> {
        val pages = mutableListOf<ConfluencePage>()
        val idRegex = """"id":"([^"]+)".*?"title":"([^"]+)"""".toRegex()

        idRegex.findAll(json).forEach { match ->
            val id = match.groupValues[1]
            val title = match.groupValues[2]
            val space = json.split("\"spaceId\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: ""
            val status = json.split("\"status\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: ""
            val body = json.split("\"body\":").getOrNull(1)?.split("\"storage\":").getOrNull(1)?.take(200)

            pages.add(ConfluencePage(id, title, space, "page", status, body?.take(200), ""))
        }

        return pages
    }

    private fun parsePage(json: String): ConfluencePage {
        return ConfluencePage(
            id = json.split("\"id\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: "",
            title = json.split("\"title\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: "",
            space = json.split("\"spaceId\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: "",
            type = json.split("\"type\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: "page",
            status = json.split("\"status\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: "",
            body = json.split("\"value\":\"").getOrNull(1)?.split("\"")?.getOrNull(0)?.take(500),
            url = json.split("\"_links\":").getOrNull(1)?.split("\"webui\":\"").getOrNull(1)?.split("\"")?.getOrNull(0) ?: ""
        )
    }

    private fun parseSpaces(json: String): List<ConfluenceSpace> {
        val spaces = mutableListOf<ConfluenceSpace>()
        val regex = """"id":"([^"]+)".*?"key":"([^"]+)".*?"name":"([^"]+)"""".toRegex()

        regex.findAll(json).forEach { match ->
            spaces.add(ConfluenceSpace(
                id = match.groupValues[1],
                key = match.groupValues[2],
                name = match.groupValues[3]
            ))
        }

        return spaces
    }
}

data class ConfluenceSpace(
    val id: String,
    val key: String,
    val name: String
)