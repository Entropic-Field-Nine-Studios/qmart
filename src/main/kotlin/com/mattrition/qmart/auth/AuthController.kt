package com.mattrition.qmart.auth

import com.mattrition.qmart.auth.dto.LoginRequest
import com.mattrition.qmart.user.dto.UserDto
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/login")
    fun login(
        @RequestBody loginRequest: LoginRequest,
        response: HttpServletResponse,
    ): ResponseEntity<UserDto> = authService.login(loginRequest, response)

    @GetMapping("/me")
    fun getAuthenticated(): ResponseEntity<UserDto> = authService.currentAuthUser()

    @PostMapping("/refresh")
    fun refreshSession(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Void> = authService.refreshSession(request, response)

    @PostMapping("/logout")
    fun logout(response: HttpServletResponse): ResponseEntity<Void> = authService.logoutUser(response)
}
