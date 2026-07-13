package com.mattrition.qmart.user

import com.mattrition.qmart.BaseH2Test
import com.mattrition.qmart.user.dto.RegistrationInfo
import com.mattrition.qmart.user.dto.UserDto
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class UserControllerTest : BaseH2Test() {
    companion object {
        const val BASE_PATH = "/api/users"
    }

    @Nested
    inner class GetUsers {
        @Test
        fun `should allow access to moderators and up`() {
            testPermissions(requestType = GET, path = BASE_PATH, minRole = UserRole.MODERATOR)
        }
    }

    @Nested
    inner class GetUserByUsername {
        @Test
        fun `should retrieve admin by username`() {
            val result =
                mockRequest(
                    requestType = GET,
                    path = "$BASE_PATH/username/aDMin",
                    accessToken = null,
                ).andExpect(status().isOk)
                    .andReturn()

            val body = result.response.contentAsString
            val user = objectMapper.readValue<UserDto>(body)

            user.username shouldBe "Admin"
        }

        @Test
        fun `should retrieve user by username`() {
            mockRequest(
                requestType = GET,
                path = "$BASE_PATH/username/test_user123",
                accessToken = null,
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.username").value("test_user123"))
        }

        @Test
        fun `should return 404 not found`() {
            mockRequest(
                requestType = GET,
                path = "$BASE_PATH/username/phantomUser210401",
                accessToken = null,
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `should retrieve current user`() {
            mockRequest(
                requestType = GET,
                path = "$BASE_PATH/me",
                accessToken = TestAccessTokens.user,
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.username").value(TestUsers.user.username))
        }
    }

    @Nested
    inner class GetUserById {
        @Test
        fun `should retrieve user by id`() {
            mockRequest(
                requestType = GET,
                path = "$BASE_PATH/${TestUsers.user.id}",
                accessToken = null,
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(TestUsers.user.id.toString()))
        }

        @Test
        fun `should return 404 not found`() {
            mockRequest(
                requestType = GET,
                path = "$BASE_PATH/${UUID.randomUUID()}",
                accessToken = null,
            ).andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class RegisterUser {
        @Test
        fun `should save user`() {
            val regInfo =
                RegistrationInfo(
                    username = "linus",
                    rawPassword = "qwerty",
                    email = "linus@linux.com",
                )

            mockRequest(requestType = POST, path = BASE_PATH, body = regInfo, accessToken = null)
                .andExpect(status().isCreated)
        }

        @Test
        fun `should return 409 conflict`() {
            val regInfo =
                RegistrationInfo(
                    username = TestUsers.user.username,
                    rawPassword = "qwerty",
                    email = "blah@test.com",
                )

            mockRequest(requestType = POST, path = BASE_PATH, body = regInfo, accessToken = null)
                .andExpect(status().isConflict)
        }
    }
}
