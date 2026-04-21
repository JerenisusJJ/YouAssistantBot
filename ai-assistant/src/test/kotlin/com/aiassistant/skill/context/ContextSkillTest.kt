package com.aiassistant.skill.context

import com.aiassistant.skill.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.sql.*

class ContextSkillTest : DescribeSpec({

    describe("ContextSkill") {

        val mockConnection = mockk<Connection>()
        val contextSkill = ContextSkill { mockConnection }

        describe("canHandle") {
            it("should handle context: prefix") {
                val request = SkillRequest(userId = "user1", message = "context:save key|value")
                contextSkill.canHandle(request) shouldBe true
            }

            it("should handle /history command") {
                val request = SkillRequest(userId = "user1", message = "/history")
                contextSkill.canHandle(request) shouldBe true
            }

            it("should handle history keyword") {
                val request = SkillRequest(userId = "user1", message = "покажи историю")
                contextSkill.canHandle(request) shouldBe true
            }
        }

        describe("handle help") {
            it("should show help for context") = runBlocking {
                val request = SkillRequest(userId = "user1", message = "/context help")
                val result = contextSkill.handle(request)
                result.success shouldBe true
            }
        }

        describe("handle history") {
            it("should return history") = runBlocking {
                val mockPreparedStatement = mockk<PreparedStatement>()
                val mockResultSet = mockk<ResultSet>()

                every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
                every { mockPreparedStatement.setString(1, "user1") } returns Unit
                every { mockPreparedStatement.setInt(2, 10) } returns Unit
                every { mockPreparedStatement.executeQuery() } returns mockResultSet
                every { mockResultSet.next() } returns false
                every { mockPreparedStatement.close() } returns Unit
                every { mockResultSet.close() } returns Unit

                val request = SkillRequest(userId = "user1", message = "/history")
                val result = contextSkill.handle(request)

                result.success shouldBe true
            }
        }
    }
})