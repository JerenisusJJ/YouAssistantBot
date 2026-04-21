package com.aiassistant.mcp

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net_URI

data class McpConfig(
    val serverUrl: String,
    val authToken: String? = null
)

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

data class McpResource(
    val uri: String,
    val name: String,
    val mimeType: String?
)

class McpClient(private val config: McpConfig) {

    private val httpClient = HttpClient.newBuilder().build()
    private val json = Json { ignoreUnknownKeys = true }

    private val tools = mutableListOf<McpTool>()
    private val resources = mutableListOf<McpResource>()

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("initialize", buildJsonObject {
                put("protocolVersion", "2024-11-05")
                put("capabilities", buildJsonObject {
                    put("tools", buildJsonObject {})
                    put("resources", buildJsonObject {})
                })
                put("clientInfo", buildJsonObject {
                    put("name", "ai-assistant")
                    put("version", "1.0.0")
                })
            })

            val response = sendRequest(request)
            if (response.statusCode() == 200) {
                parseTools(response.body())
                Result.success(Unit)
            } else {
                Result.failure(Exception("Init failed: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listTools(): Result<List<McpTool>> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("tools/list", buildJsonObject {})
            val response = sendRequest(request)

            if (response.statusCode() == 200) {
                val parsed = json.parseToJsonElement(response.body())
                val toolsArray = parsed.jsonObject["tools"]?.jsonArray

                val resultTools = mutableListOf<McpTool>()
                toolsArray?.forEach { toolElem ->
                    val tool = toolElem.jsonObject
                    resultTools.add(McpTool(
                        name = tool["name"]?.jsonPrimitive?.content ?: "",
                        description = tool["description"]?.jsonPrimitive?.content ?: "",
                        inputSchema = tool["inputSchema"]?.jsonObject ?: buildJsonObject {}
                    ))
                }

                tools.clear()
                tools.addAll(resultTools)
                Result.success(resultTools)
            } else {
                Result.failure(Exception("List tools failed: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun callTool(toolName: String, arguments: Map<String, Any>): Result<JsonElement> = withContext(Dispatchers.IO) {
        try {
            val argsJson = buildJsonObject {
                arguments.forEach { (key, value) ->
                    put(key, encodeValue(value))
                }
            }

            val request = buildRequest("tools/call", buildJsonObject {
                put("name", toolName)
                put("arguments", argsJson)
            })

            val response = sendRequest(request)

            if (response.statusCode() == 200) {
                val parsed = json.parseToJsonElement(response.body())
                val content = parsed.jsonObject["content"]?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content

                if (content != null) {
                    Result.success(json.parseToJsonElement(content))
                } else {
                    Result.success(parsed)
                }
            } else {
                Result.failure(Exception("Tool call failed: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listResources(): Result<List<McpResource>> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("resources/list", buildJsonObject {})
            val response = sendRequest(request)

            if (response.statusCode() == 200) {
                val parsed = json.parseToJsonElement(response.body())
                val resourcesArray = parsed.jsonObject["resources"]?.jsonArray

                val resultResources = mutableListOf<McpResource>()
                resourcesArray?.forEach { resElem ->
                    val res = resElem.jsonObject
                    resultResources.add(McpResource(
                        uri = res["uri"]?.jsonPrimitive?.content ?: "",
                        name = res["name"]?.jsonPrimitive?.content ?: "",
                        mimeType = res["mimeType"]?.jsonPrimitive?.content
                    ))
                }

                resources.clear()
                resources.addAll(resultResources)
                Result.success(resultResources)
            } else {
                Result.failure(Exception("List resources failed: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readResource(uri: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("resources/read", buildJsonObject {
                put("uri", uri)
            })

            val response = sendRequest(request)

            if (response.statusCode() == 200) {
                val parsed = json.parseToJsonElement(response.body())
                val content = parsed.jsonObject["contents"]?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content

                Result.success(content ?: "")
            } else {
                Result.failure(Exception("Read resource failed: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildRequest(method: String, params: JsonObject): HttpRequest {
        val bodyJson = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", method)
            put("params", params)
        }

        return HttpRequest.newBuilder()
            .uri(URI.create("${config.serverUrl}/v1/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .config.authToken?.let { header("Authorization", "Bearer $it") }
            .POST(HttpRequest.BodyPublishers.ofString(bodyJson.toString()))
            .build()
    }

    private fun sendRequest(request: HttpRequest): HttpResponse<String> {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun parseTools(response: String) {
        try {
            val parsed = json.parseToJsonElement(response)
            val toolsArray = parsed.jsonObject["tools"]?.jsonArray

            toolsArray?.forEach { toolElem ->
                val tool = toolElem.jsonObject
                tools.add(McpTool(
                    name = tool["name"]?.jsonPrimitive?.content ?: "",
                    description = tool["description"]?.jsonPrimitive?.content ?: "",
                    inputSchema = tool["inputSchema"]?.jsonObject ?: buildJsonObject {}
                ))
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
    }

    private fun JsonBuilder.encodeValue(value: Any): JsonElement {
        return when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> {
                val obj = buildJsonObject {
                    value.forEach { (k, v) ->
                        put(k.toString(), encodeValue(v))
                    }
                }
                obj
            }
            is List<*> -> {
                JsonArray(value.map { encodeValue(it) })
            }
            else -> JsonPrimitive(value.toString())
        }
    }

    fun getAvailableTools(): List<McpTool> = tools.toList()
    fun getAvailableResources(): List<McpResource> = resources.toList()
}

fun buildJsonObject(block: JsonObjectBuilder.() -> Unit): JsonObject {
    return JsonObjectBuilder().apply(block).build()
}

class JsonObjectBuilder {
    private val map = mutableMapOf<String, JsonElement>()

    fun put(key: String, value: JsonElement) {
        map[key] = value
    }

    fun build(): JsonObject = JsonObject(map)
}