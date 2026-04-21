package com.aiassistant.skill.admin

import com.aiassistant.skill.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import kotlinx.coroutines.runBlocking

class AdminSkillTest : DescribeSpec({

    val registry = mockk<SkillRegistry>()
    val cailaApiKey = "test-key"

    describe("AdminSkill") {
        val adminSkill = AdminSkill(registry, cailaApiKey)

        describe("canHandle") {
            it("should always handle any request") {
                val request = SkillRequest(userId = "user1", message = "any message")
                adminSkill.canHandle(request) shouldBe true
            }
        }

        describe("handle") {
            it("should classify work request") {
                val workSkill = mockk<Skill>()
                every { workSkill.name } returns "jira"
                every { workSkill.canHandle(any()) } returns true
                coEvery { workSkill.handle(any()) } returns SkillResponse(
                    success = true,
                    message = "Task created",
                    skillName = "jira"
                )
                every { registry.findSkillFor(any()) } returns workSkill

                val request = SkillRequest(userId = "user1", message = "создай задачу в Jira")
                val result = runBlocking { adminSkill.handle(request) }

                result.success shouldBe true
                result.skillName shouldBe "jira"
            }

            it("should return error when no skill found") {
                every { registry.findSkillFor(any()) } returns null

                val request = SkillRequest(userId = "user1", message = "привет")
                val result = runBlocking { adminSkill.handle(request) }

                result.success shouldBe false
            }
        }

        describe("classifyContext") {
            it("should detect work keywords") {
                val request = SkillRequest(userId = "user1", message = "создай задачу")
                adminSkill.canHandle(request) shouldBe true
            }

            it("should detect personal keywords") {
                val request = SkillRequest(userId = "user1", message = "запиши заметку")
                adminSkill.canHandle(request) shouldBe true
            }
        }
    }

    describe("SkillRegistry") {
        val registry = SkillRegistry()

        it("should register and retrieve skills") {
            val skill = object : Skill {
                override val name = "test"
                override val description = "test skill"
                override suspend fun handle(request: SkillRequest) = SkillResponse(true, "ok")
                override fun canHandle(request: SkillRequest) = true
            }

            registry.register(skill)
            registry.get("test") shouldNotBe null
            registry.get("test")?.name shouldBe "test"
        }

        it("should find skill for request") {
            val skill = object : Skill {
                override val name = "finder"
                override val description = "finder"
                override suspend fun handle(request: SkillRequest) = SkillResponse(true, "ok")
                override fun canHandle(request: SkillRequest) = request.message.contains("find")
            }

            registry.register(skill)
            val found = registry.findSkillFor(SkillRequest("u1", "please find something"))
            found?.name shouldBe "finder"
        }
    }
})