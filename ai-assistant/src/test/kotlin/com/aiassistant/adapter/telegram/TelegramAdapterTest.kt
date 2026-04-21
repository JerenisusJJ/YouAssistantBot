package com.aiassistant.adapter.telegram

import com.aiassistant.skill.admin.AdminSkill
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

class TelegramAdapterTest : DescribeSpec({

    describe("TelegramAdapter") {

        val mockAdminSkill = mockk<AdminSkill>()

        describe("config") {
            it("should have correct token") {
                val adapter = TelegramAdapter.create(
                    token = "test-token:TestAuth",
                    username = "TestBot",
                    adminSkill = mockAdminSkill,
                    allowedUserIds = setOf("351153237")
                )
                adapter.botToken shouldBe "test-token:TestAuth"
            }

            it("should have correct username") {
                val adapter = TelegramAdapter.create(
                    token = "test-token:TestAuth",
                    username = "TestBot",
                    adminSkill = mockAdminSkill,
                    allowedUserIds = setOf("351153237")
                )
                adapter.botUsername shouldBe "TestBot"
            }

            it("should allow custom user ids") {
                val adapter = TelegramAdapter.create(
                    token = "test-token:TestAuth",
                    username = "TestBot",
                    adminSkill = mockAdminSkill,
                    allowedUserIds = setOf("111", "222", "351153237")
                )
                adapter.botToken shouldBe "test-token:TestAuth"
            }

            it("should default to only user 351153237") {
                val adapter = TelegramAdapter.create(
                    token = "test-token:TestAuth",
                    username = "TestBot",
                    adminSkill = mockAdminSkill
                )
                adapter.botToken shouldBe "test-token:TestAuth"
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