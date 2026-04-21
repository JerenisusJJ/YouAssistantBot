package com.aiassistant.skill

class SkillRegistry {
    private val skills = mutableMapOf<String, Skill>()

    fun register(skill: Skill) {
        skills[skill.name] = skill
    }

    fun get(name: String): Skill? = skills[name]

    fun all(): List<Skill> = skills.values.toList()

    fun findSkillFor(request: SkillRequest): Skill? {
        return skills.values.find { it.canHandle(request) }
    }
}