package com.aiassistant.skill

data class SkillRequest(
    val userId: String,
    val message: String,
    val context: Map<String, Any> = emptyMap()
)

data class SkillResponse(
    val success: Boolean,
    val message: String,
    val data: Any? = null,
    val skillName: String? = null
)

interface Skill {
    val name: String
    val description: String

    suspend fun handle(request: SkillRequest): SkillResponse

    fun canHandle(request: SkillRequest): Boolean
}