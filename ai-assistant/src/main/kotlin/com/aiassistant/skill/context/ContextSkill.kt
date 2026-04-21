package com.aiassistant.skill.context

import com.aiassistant.skill.*
import kotlinx.coroutines.*
import java.sql.*

class ContextSkill(
    private val connection: () -> Connection
) : Skill {

    override val name = "context"
    override val description = "Dialog history management"

    override fun canHandle(request: SkillRequest): Boolean {
        val msg = request.message.lowercase()
        return msg.startsWith("context:") ||
               msg.contains("истори") ||
               msg.contains("history") ||
               msg.startsWith("/history") ||
               msg.startsWith("/context")
    }

    override suspend fun handle(request: SkillRequest): SkillResponse = withContext(Dispatchers.IO) {
        val message = request.message

        try {
            when {
                message.startsWith("context:save") -> handleSave(request)
                message.startsWith("context:get") -> handleGet(request)
                message.startsWith("/history") -> handleHistory(request)
                message.startsWith("/context clear") -> handleClear(request)
                else -> handleMessage(request)
            }
        } catch (e: Exception) {
            SkillResponse(false, "Error: ${e.message}", skillName = name)
        }
    }

    private fun handleSave(request: SkillRequest): SkillResponse {
        val parts = request.message.removePrefix("context:save").trim().split("|", limit = 2)
        val key = parts.getOrNull(0)?.trim() ?: "default"
        val value = parts.getOrNull(1)?.trim() ?: ""

        val conn = connection()
        val stmt = conn.prepareStatement(
            "INSERT INTO context (user_id, session_id, skill_name, message, context_type) VALUES (?, ?, ?, ?, ?) " +
            "ON CONFLICT DO UPDATE SET message = ?, updated_at = CURRENT_TIMESTAMP"
        )

        stmt.setString(1, request.userId)
        stmt.setString(2, getSessionId(request))
        stmt.setString(3, "context")
        stmt.setString(4, "$key: $value")
        stmt.setString(5, request.context["contextType"]?.toString() ?: "common")
        stmt.setString(6, "$key: $value")

        return stmt.use { s ->
            s.executeUpdate()
            SkillResponse(true, "Context saved: $key = $value", name = name)
        }
    }

    private fun handleGet(request: SkillRequest): SkillResponse {
        val key = request.message.removePrefix("context:get").trim()

        val conn = connection()
        val stmt = conn.prepareStatement(
            "SELECT message FROM context WHERE user_id = ? ORDER BY created_at DESC LIMIT 1"
        )
        stmt.setString(1, request.userId)

        return stmt.use { s ->
            val rs = s.executeQuery()
            if (rs.next()) {
                val message = rs.getString("message")
                SkillResponse(true, message, message, name)
            } else {
                SkillResponse(true, "No context found", name = name)
            }
        }
    }

    private fun handleHistory(request: SkillRequest): SkillResponse {
        val conn = connection()
        val limit = request.message.removePrefix("/history").trim().toIntOrNull() ?: 10

        val stmt = conn.prepareStatement(
            "SELECT message, skill_name, created_at FROM context " +
            "WHERE user_id = ? ORDER BY created_at DESC LIMIT ?"
        )
        stmt.setString(1, request.userId)
        stmt.setInt(2, limit)

        return stmt.use { s ->
            val rs = s.executeQuery()
            val history = mutableListOf<String>()

            while (rs.next()) {
                val msg = rs.getString("message")
                val skill = rs.getString("skill_name")
                val time = rs.getTimestamp("created_at")
                history.add("${time}: [$skill] $msg")
            }

            if (history.isEmpty()) {
                SkillResponse(true, "No history", name = name)
            } else {
                SkillResponse(true, history.joinToString("\n"), history, name)
            }
        }
    }

    private fun handleClear(request: SkillRequest): SkillResponse {
        val conn = connection()
        val stmt = conn.prepareStatement("DELETE FROM context WHERE user_id = ?")
        stmt.setString(1, request.userId)

        return stmt.use { s ->
            val deleted = s.executeUpdate()
            SkillResponse(true, "Cleared $deleted messages", name = name)
        }
    }

    private fun handleMessage(request: SkillRequest): SkillResponse {
        val conn = connection()
        val stmt = conn.prepareStatement(
            "INSERT INTO context (user_id, session_id, skill_name, message, context_type) VALUES (?, ?, ?, ?, ?)"
        )

        stmt.setString(1, request.userId)
        stmt.setString(2, getSessionId(request))
        stmt.setString(3, request.context["originalSkill"]?.toString() ?: "unknown")
        stmt.setString(4, request.message.take(500))
        stmt.setString(5, request.context["contextType"]?.toString() ?: "common")

        return stmt.use { s ->
            s.executeUpdate()
            SkillResponse(true, "Message saved to context", name = name)
        }
    }

    private fun getSessionId(request: SkillRequest): String {
        return "session_${request.userId}_${System.currentTimeMillis() / 3600000}"
    }
}