package com.aiassistant.skill.admin

import com.aiassistant.skill.*

class AdminSkill(
    private val registry: SkillRegistry,
    private val cailaApiKey: String
) : Skill {

    override val name = "admin"
    override val description = "Router skill - determines work/personal and selects subskill"

    private val workKeywords = listOf(
        "jira", "gitlab", "confluence", "задача", "merge", "pull request",
        "commit", "pipeline", "тест", "баг", "issue", "mr", "code review"
    )

    private val personalKeywords = listOf(
        "заметка", "напоминание", "рецепт", "личное", "файл", "запиши"
    )

    private val workPatterns = workKeywords.joinToString("|")
    private val personalPatterns = personalKeywords.joinToString("|")

    override fun canHandle(request: SkillRequest): Boolean {
        return true
    }

    override suspend fun handle(request: SkillRequest): SkillResponse {
        val message = request.message.lowercase()

        val contextType = classifyContext(message)
        val targetSkill = findTargetSkill(request, contextType)

        return if (targetSkill != null) {
            val forwardedRequest = request.copy(
                context = request.context + mapOf(
                    "contextType" to contextType,
                    "originalSkill" to name
                )
            )
            targetSkill.handle(forwardedRequest).copy(
                data = (targetSkill.handle(forwardedRequest).data as? Map<*, *>)?.plus(
                    mapOf("contextType" to contextType)
                ) ?: mapOf("contextType" to contextType)
            )
        } else {
            SkillResponse(
                success = false,
                message = "Не могу определить скилл для запроса: ${request.message}",
                skillName = name
            )
        }
    }

    private fun classifyContext(message: String): String {
        val hasWork = workPatterns.toRegex().containsMatchIn(message)
        val hasPersonal = personalPatterns.toRegex().containsMatchIn(message)

        return when {
            hasWork && !hasPersonal -> "work"
            hasPersonal && !hasPersonal -> "personal"
            hasWork && hasPersonal -> "common"
            else -> "common"
        }
    }

    private fun findTargetSkill(request: SkillRequest, contextType: String): Skill? {
        return registry.findSkillFor(request)
    }
}
