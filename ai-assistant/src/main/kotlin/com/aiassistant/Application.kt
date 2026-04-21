package com.aiassistant

import com.aiassistant.adapter.telegram.TelegramAdapter
import com.aiassistant.db.DbSkill
import com.aiassistant.integration.mattermost.MattermostClient
import com.aiassistant.integration.mattermost.MattermostSkill
import com.aiassistant.skill.SkillRegistry
import com.aiassistant.skill.admin.AdminSkill
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.telegram.telegrambots.longpolling.TelegramLongPollingBot
import java.sql.DriverManager
import java.sql.SQLException

fun main() {
    val env = System.getenv()

    val dbUrl = "jdbc:postgresql://${env["DB_HOST"] ?: "localhost"}:${env["DB_PORT"] ?: "5432"}/${env["DB_NAME"] ?: "ai_assistant"}"
    val dbUser = env["DB_USER"] ?: "ai_user"
    val dbPassword = env["DB_PASSWORD"] ?: ""

    val dbConnection: () -> java.sql.Connection = {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword)
    }

    val skillRegistry = SkillRegistry()

    val dbSkill = DbSkill(dbConnection)
    skillRegistry.register(dbSkill)

    val cailaToken = env["CAILA_TOKEN"] ?: ""

    var mattermostSkill: MattermostSkill? = null
    val mattermostUrl = env["MATTERMOST_URL"]
    val mattermostToken = env["MATTERMOST_TOKEN"]
    if (!mattermostUrl.isNullOrBlank() && !mattermostToken.isNullOrBlank()) {
        val mattermostClient = MattermostClient(
            com.aiassistant.integration.mattermost.MattermostConfig(
                baseUrl = mattermostUrl,
                botToken = mattermostToken,
                teamName = env["MATTERMOST_TEAM"] ?: ""
            )
        )
        mattermostSkill = MattermostSkill(mattermostClient)
        skillRegistry.register(mattermostSkill)
    }

    val adminSkill = AdminSkill(skillRegistry, cailaToken)
    skillRegistry.register(adminSkill)

    val telegramToken = env["TELEGRAM_BOT_TOKEN"]
    if (!telegramToken.isNullOrBlank()) {
        val allowedUsers = (env["TELEGRAM_ALLOWED_USERS"] ?: "351153237")
            .split(",")
            .map { it.trim() }
            .toSet()

        val telegramBot = TelegramAdapter.create(
            token = telegramToken,
            username = env["TELEGRAM_BOT_USERNAME"] ?: "AIAssistantBot",
            adminSkill = adminSkill,
            allowedUserIds = allowedUsers
        )

        val executor = TelegramLongPollingBot(telegramBot)
        executor.start()
    }

    embeddedServer(Netty, port = env["PORT"]?.toIntOrNull() ?: 8080) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    // Ktor server endpoints can be added here if needed
}
