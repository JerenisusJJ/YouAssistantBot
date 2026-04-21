package com.aiassistant.integration.jira

import com.aiassistant.skill.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import kotlinx.coroutines.runBlocking

class JiraSkillTest : DescribeSpec({

    describe("JiraSkill") {

        val mockClient = mockk<JiraClient>()
        val skill = JiraSkill(mockClient, "PROJ")

        describe("canHandle") {
            it("should handle jira keyword") {
                val request = SkillRequest(userId = "user1", message = "jira задачи")
                skill.canHandle(request) shouldBe true
            }

            it("should handle task keyword") {
                val request = SkillRequest(userId = "user1", message = "создай задачу")
                skill.canHandle(request) shouldBe true
            }

            it("should handle issue keyword") {
                val request = SkillRequest(userId = "user1", message = "посмотри issue")
                skill.canHandle(request) shouldBe true
            }
        }

        describe("handle help") {
            it("should show help") = runBlocking {
                val request = SkillRequest(userId = "user1", message = "/jira help")
                val result = skill.handle(request)

                result.success shouldBe true
                result.message shouldContain "Jira Skills"
            }
        }

        describe("handle search") {
            it("should return issues") = runBlocking {
                coEvery { mockClient.searchIssues(any()) } returns Result.success(
                    listOf(JiraIssue("PROJ-1", "Test issue", "desc", "Open", null, null))
                )

                val request = SkillRequest(userId = "user1", message = "/jira search assignee = me")
                val result = skill.handle(request)

                result.success shouldBe true
            }
        }

        describe("handle create") {
            it("should create issue") = runBlocking {
                coEvery { mockClient.createIssue(any(), any(), any()) } returns Result.success(
                    JiraIssue("PROJ-2", "New issue", "desc", "Open", null, null)
                )

                val request = SkillRequest(userId = "user1", message = "/jira create Test task | Description")
                val result = skill.handle(request)

                result.success shouldBe true
            }
        }
    }
})