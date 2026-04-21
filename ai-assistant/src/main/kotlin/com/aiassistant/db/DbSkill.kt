package com.aiassistant.db

import com.aiassistant.skill.*
import kotlinx.coroutines.*
import java.sql.*
import java.util.*

class DbSkill(
    private val connection: () -> Connection
) : Skill {

    override val name = "db"
    override val description = "Database CRUD operations for configs"

    private val encryptKey: String = System.getenv("ENCRYPT_KEY") ?: "default-dev-key-change-me"

    override fun canHandle(request: SkillRequest): Boolean {
        val msg = request.message.lowercase()
        return msg.startsWith("db:") ||
               msg.contains("config") ||
               msg.contains("настройк")
    }

    override suspend fun handle(request: SkillRequest): SkillResponse = withContext(Dispatchers.IO) {
        val parts = request.message.removePrefix("db:").trim().split(" ", limit = 2)
        val command = parts.getOrNull(0)?.lowercase() ?: ""
        val args = parts.getOrNull(1) ?: ""

        try {
            when (command) {
                "get" -> handleGet(args)
                "set" -> handleSet(args)
                "list" -> handleList()
                "delete", "del" -> handleDelete(args)
                else -> help()
            }
        } catch (e: Exception) {
            SkillResponse(
                success = false,
                message = "Error: ${e.message}",
                skillName = name
            )
        }
    }

    private fun handleGet(key: String): SkillResponse {
        if (key.isBlank()) {
            return SkillResponse(false, "Usage: db:get <key>", name = name)
        }

        val conn = connection()
        val stmt = conn.prepareStatement("SELECT encrypted_value FROM config WHERE key = ?")
        stmt.setString(1, key)

        return stmt.use { s ->
            val rs = s.executeQuery()
            if (rs.next()) {
                val encrypted = rs.getString("encrypted_value")
                val decrypted = decrypt(encrypted)
                SkillResponse(true, "Value for '$key': $decrypted", decrypted, name)
            } else {
                SkillResponse(false, "Key '$key' not found", name = name)
            }
        }
    }

    private fun handleSet(args: String): SkillResponse {
        val parts = args.split(" ", limit = 2)
        val key = parts.getOrNull(0) ?: return SkillResponse(false, "Usage: db:set <key> <value>", name = name)
        val value = parts.getOrNull(1) ?: return SkillResponse(false, "Usage: db:set <key> <value>", name = name)

        val conn = connection()
        val stmt = conn.prepareStatement(
            "INSERT INTO config (key, encrypted_value) VALUES (?, ?) " +
            "ON CONFLICT (key) DO UPDATE SET encrypted_value = ?, updated_at = CURRENT_TIMESTAMP"
        )
        val encrypted = encrypt(value)
        stmt.setString(1, key)
        stmt.setString(2, encrypted)
        stmt.setString(3, encrypted)

        return stmt.use { s ->
            s.executeUpdate()
            SkillResponse(true, "Config '$key' saved", name = name)
        }
    }

    private fun handleList(): SkillResponse {
        val conn = connection()
        val stmt = conn.prepareStatement("SELECT key, description FROM config")

        return stmt.use { s ->
            val rs = s.executeQuery()
            val configs = mutableListOf<String>()
            while (rs.next()) {
                val key = rs.getString("key")
                val desc = rs.getString("description") ?: ""
                configs.add("$key${if (desc.isNotBlank()) " - $desc" else ""}")
            }

            if (configs.isEmpty()) {
                SkillResponse(true, "No configs found", emptyList(), name)
            } else {
                SkillResponse(true, configs.joinToString("\n"), configs, name)
            }
        }
    }

    private fun handleDelete(key: String): SkillResponse {
        if (key.isBlank()) {
            return SkillResponse(false, "Usage: db:delete <key>", name = name)
        }

        val conn = connection()
        val stmt = conn.prepareStatement("DELETE FROM config WHERE key = ?")
        stmt.setString(1, key)

        return stmt.use { s ->
            val deleted = s.executeUpdate()
            if (deleted > 0) {
                SkillResponse(true, "Config '$key' deleted", name = name)
            } else {
                SkillResponse(false, "Key '$key' not found", name = name)
            }
        }
    }

    private fun help(): SkillResponse {
        return SkillResponse(
            true,
            """
            |DB Skills:
            |  db:get <key>     - Get config value
            |  db:set <key> <value> - Set config value
            |  db:list          - List all configs
            |  db:delete <key>  - Delete config
            """.trimMargin(),
            name = name
        )
    }

    private fun encrypt(value: String): String {
        return Base64.getEncoder().encodeToString(value.toByteArray())
    }

    private fun decrypt(encrypted: String): String {
        return String(Base64.getDecoder().decode(encrypted))
    }
}