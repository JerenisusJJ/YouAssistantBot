package com.aiassistant.adapter.telegram

import com.aiassistant.skill.*
import com.aiassistant.skill.admin.AdminSkill
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.longpolling.LongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage

class TelegramAdapter(
    private val adminSkill: AdminSkill,
    private val allowedUserIds: Set<String> = setOf("351153237")
) : LongPollingBot() {

    lateinit var botToken: String
    lateinit var botUsername: String

    override fun getBotToken(): String = botToken

    override fun getBotUsername(): String = botUsername

    override fun onUpdateReceived(update: Update) {
        if (!update.hasMessage() || !update.message.hasText()) return

        val message = update.message
        val userId = message.from.id.toString()
        val text = message.text
        val chatId = message.chatId.toString()

        if (text.startsWith("/")) return

        if (userId !in allowedUserIds) {
            val reply = SendMessage.builder()
                .chatId(chatId)
                .text("Доступ запрещён. Ваш ID: $userId")
                .build()
            execute(reply)
            return
        }

        val request = SkillRequest(userId = userId, message = text)

        val response = kotlinx.coroutines.runBlocking {
            adminSkill.handle(request)
        }

        val reply = SendMessage.builder()
            .chatId(chatId)
            .text(response.message)
            .build()

        execute(reply)
    }

    companion object {
        fun create(
            token: String,
            username: String,
            adminSkill: AdminSkill,
            allowedUserIds: Set<String> = setOf("351153237")
        ): TelegramAdapter {
            val adapter = TelegramAdapter(adminSkill, allowedUserIds)
            adapter.botToken = token
            adapter.botUsername = username
            return adapter
        }
    }
}
