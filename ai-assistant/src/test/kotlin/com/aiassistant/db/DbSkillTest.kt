package com.aiassistant.db

import com.aiassistant.skill.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import java.sql.*
import kotlinx.coroutines.runBlocking

class DbSkillTest : DescribeSpec({

    describe("DbSkill") {

        val mockConnection = mockk<Connection>()
        val connectionFactory: () -> Connection = { mockConnection }
        val dbSkill = DbSkill(connectionFactory)

        describe("canHandle") {
            it("should handle db: prefix") {
                val request = SkillRequest(userId = "user1", message = "db:get jira_token")
                dbSkill.canHandle(request) shouldBe true
            }

            it("should handle config keyword") {
                val request = SkillRequest(userId = "user1", message = "config list")
                dbSkill.canHandle(request) shouldBe true
            }

            it("should not handle unrelated messages") {
                val request = SkillRequest(userId = "user1", message = "create jira task")
                dbSkill.canHandle(request) shouldBe false
            }
        }

        describe("handle get") {
            it("should return value when key exists") {
                val mockPreparedStatement = mockk<PreparedStatement>()
                val mockResultSet = mockk<ResultSet>()

                every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
                every { mockPreparedStatement.setString(1, "jira_token") } returns Unit
                every { mockPreparedStatement.executeQuery() } returns mockResultSet
                every { mockResultSet.next() } returns true
                every { mockResultSet.getString("encrypted_value") } returns "c2VjcmV0"
                every { mockPreparedStatement.close() } returns Unit
                every { mockResultSet.close() } returns Unit

                val request = SkillRequest(userId = "user1", message = "db:get jira_token")
                val result = runBlocking { dbSkill.handle(request) }

                result.success shouldBe true
                result.message shouldContain "jira_token"
            }

            it("should return error when key not found") {
                val mockPreparedStatement = mockk<PreparedStatement>()
                val mockResultSet = mockk<ResultSet>()

                every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
                every { mockPreparedStatement.setString(1, "missing_key") } returns Unit
                every { mockPreparedStatement.executeQuery() } returns mockResultSet
                every { mockResultSet.next() } returns false
                every { mockPreparedStatement.close() } returns Unit
                every { mockResultSet.close() } returns Unit

                val request = SkillRequest(userId = "user1", message = "db:get missing_key")
                val result = runBlocking { dbSkill.handle(request) }

                result.success shouldBe false
            }
        }

        describe("handle set") {
            it("should save config") {
                val mockPreparedStatement = mockk<PreparedStatement>()

                every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
                every { mockPreparedStatement.setString(any(), any()) } returns Unit
                every { mockPreparedStatement.executeUpdate() } returns 1
                every { mockPreparedStatement.close() } returns Unit

                val request = SkillRequest(userId = "user1", message = "db:set jira_token my-secret-value")
                val result = runBlocking { dbSkill.handle(request) }

                result.success shouldBe true
                result.message shouldContain "saved"
            }
        }

        describe("handle list") {
            it("should list all configs") {
                val mockPreparedStatement = mockk<PreparedStatement>()
                val mockResultSet = mockk<ResultSet>()

                every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
                every { mockPreparedStatement.executeQuery() } returns mockResultSet
                every { mockResultSet.next() } returnsMany listOf(true, true, false)
                every { mockResultSet.getString("key") } returnsMany listOf("key1", "key2")
                every { mockResultSet.getString("description") } returnsMany listOf("desc1", "")
                every { mockPreparedStatement.close() } returns Unit
                every { mockResultSet.close() } returns Unit

                val request = SkillRequest(userId = "user1", message = "db:list")
                val result = runBlocking { dbSkill.handle(request) }

                result.success shouldBe true
            }
        }

        describe("handle delete") {
            it("should delete config") {
                val mockPreparedStatement = mockk<PreparedStatement>()

                every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
                every { mockPreparedStatement.setString(1, "key_to_delete") } returns Unit
                every { mockPreparedStatement.executeUpdate() } returns 1
                every { mockPreparedStatement.close() } returns Unit

                val request = SkillRequest(userId = "user1", message = "db:delete key_to_delete")
                val result = runBlocking { dbSkill.handle(request) }

                result.success shouldBe true
                result.message shouldContain "deleted"
            }
        }
    }
})