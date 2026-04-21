package com.aiassistant.integration.mattermost

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import kotlinx.coroutines.*

class MattermostClientTest : DescribeSpec({

    describe("MattermostConfig") {
        it("should create config from environment variables") {
            val config = MattermostConfig(
                baseUrl = "https://mattermost.company.com",
                botToken = "test-token",
                teamName = "my-team"
            )

            config.baseUrl shouldBe "https://mattermost.company.com"
            config.botToken shouldBe "test-token"
            config.teamName shouldBe "my-team"
        }
    }

    describe("MattermostMessage") {
        it("should create message") {
            val msg = MattermostMessage(
                channelId = "channel123",
                message = "Hello",
                userId = "user456"
            )

            msg.channelId shouldBe "channel123"
            msg.message shouldBe "Hello"
            msg.userId shouldBe "user456"
        }
    }

    describe("Team") {
        it("should create team") {
            val team = Team("team-id", "team-name")

            team.id shouldBe "team-id"
            team.name shouldBe "team-name"
        }
    }

    describe("MattermostSkill") {

        val mockClient = mockk<MattermostClient>()
        val skill = MattermostSkill(mockClient)

        describe("canHandle") {
            it("should handle /msg command") {
                val request = com.aiassistant.skill.SkillRequest(
                    userId = "user1",
                    message = "/msg general hello"
                )
                skill.canHandle(request) shouldBe true
            }

            it("should handle /channel command") {
                val request = com.aiassistant.skill.SkillRequest(
                    userId = "user1",
                    message = "/channel random message"
                )
                skill.canHandle(request) shouldBe true
            }

            it("should handle /teams command") {
                val request = com.aiassistant.skill.SkillRequest(
                    userId = "user1",
                    message = "/teams"
                )
                skill.canHandle(request) shouldBe true
            }

            it("should handle russian messages") {
                val request = com.aiassistant.skill.SkillRequest(
                    userId = "user1",
                    message = "напиши в канал"
                )
                skill.canHandle(request) shouldBe true
            }

            it("should not handle unrelated messages") {
                val request = com.aiassistant.skill.SkillRequest(
                    userId = "user1",
                    message = "create jira task"
                )
                skill.canHandle(request) shouldBe false
            }
        }

        describe("handle help") {
            it("should return help message") = runBlocking {
                val request = com.aiassistant.skill.SkillRequest(
                    userId = "user1",
                    message = "/help"
                )
                val result = skill.handle(request)

                result.success shouldBe true
                result.message shouldContain "Mattermost Skills"
            }
        }

        describe("handle /teams") {
            it("should return list of teams") = runBlocking {
                coEvery { mockClient.listTeams() } returns Result.success(
                    listOf(Team("id1", "team1"), Team("id2", "team2"))
                )

                val request = com.aiassistant.skill.SkillRequest(
                    userId = "user1",
                    message = "/teams"
                )
                val result = skill.handle(request)

                result.success shouldBe true
                result.message shouldContain "team1"
            }

            it("should handle error when no teams") = runBlocking {
                coEvery { mockClient.listTeams() } returns Result.failure(Exception("No teams"))

                val request = com.aiassistant.skill.SkillRequest(
                    userId = "user1",
                    message = "/teams"
                )
                val result = skill.handle(request)

                result.success shouldBe false
            }
        }
    }
})