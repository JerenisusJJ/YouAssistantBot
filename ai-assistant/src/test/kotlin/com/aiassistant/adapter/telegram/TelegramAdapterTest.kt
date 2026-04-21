package com.aiassistant.adapter.telegram

import com.aiassistant.skill.admin.AdminSkill
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

class TelegramAdapterTest : DescribeSpec({

    describe("TelegramAdapter") {

        val mockAdminSkill = mockk<AdminSkill>()
        lateinit var adapter: TelegramAdapter

        beforeEach {
            adapter = TelegramAdapter.create(
                token = "test-token:TestAuth",
                username = "TestBot",
                adminSkill = mockAdminSkill
            )
        }

        describe("config") {
            it("should have correct token") {
                adapter.botToken shouldBe "test-token:TestAuth"
            }

            it("should have correct username") {
                adapter.botUsername shouldBe "TestBot"
            }

            it("should return token from getBotToken") {
                adapter.botToken shouldBe "test-token:TestAuth"
            }

            it("should return username from getBotUsername") {
                adapter.botUsername shouldBe "TestBot"
            }
        }

        describe("companion object") {
            it("should create adapter with token and username") {
                val createdAdapter = TelegramAdapter.create(
                    token = "123456:AAHdqTzxHA-npmwW4-2B7F7k",
                    username = "MyTestBot",
                    adminSkill = mockAdminSkill
                )

                createdAdapter.botToken shouldBe "123456:AAHdqTzxHA-npmwW4-2B7F7k"
                createdAdapter.botUsername shouldBe "MyTestBot"
            }
        }
    }
})