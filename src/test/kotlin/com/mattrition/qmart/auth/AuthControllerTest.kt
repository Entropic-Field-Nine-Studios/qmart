package com.mattrition.qmart.auth

import com.mattrition.qmart.BaseH2Test
import com.mattrition.qmart.auth.dto.LoginRequest
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AuthControllerTest : BaseH2Test() {
    companion object {
        const val BASE_PATH = "/api/auth"
        const val LOGIN = "$BASE_PATH/login"
    }

    @Test
    fun `should login user`() {
        val loginReq =
            LoginRequest(username = TestUsers.user.username, rawPassword = "IloveToTravel!")

        mockRequest(POST, LOGIN, body = loginReq, accessToken = null).andExpect(status().isOk)
    }

    @Test
    fun `non-existing user should return 400 bad request`() {
        val loginReq = LoginRequest(username = "1d9ub3fhu9", rawPassword = "123456")

        mockRequest(POST, LOGIN, body = loginReq, accessToken = null)
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `invalid password should return 400 bad request`() {
        val loginReq = LoginRequest(username = TestUsers.user.username, rawPassword = "1234561427")

        mockRequest(POST, LOGIN, body = loginReq, accessToken = null)
            .andExpect(status().isBadRequest)
    }
}
