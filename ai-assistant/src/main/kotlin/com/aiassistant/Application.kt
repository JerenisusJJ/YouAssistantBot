package com.aiassistant

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 8080) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    // Core modules will be added here
    // - Skill router (admin)
    // - DB connection
    // - Context manager
    // - Telegram/Mattermost adapters
}
