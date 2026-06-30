package com.mattrition.qmart.auth

import com.mattrition.qmart.auth.dto.LoginRequest
import com.mattrition.qmart.exception.BadRequestException
import com.mattrition.qmart.exception.ForbiddenException
import com.mattrition.qmart.exception.NotFoundException
import com.mattrition.qmart.user.UserRepository
import com.mattrition.qmart.user.dto.UserDto
import com.mattrition.qmart.user.mapper.UserMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
    private val cookieService: CookieService,
    private val userDetailsService: CustomUserDetailsService,
) {
    /**
     * Validates the provided credentials against a user in the database, then creates a new Java
     * Web Token for the user.
     *
     * @throws BadRequestException If the username does not exist OR the password does not match.
     */
    fun login(
        loginRequest: LoginRequest,
        response: HttpServletResponse,
    ): ResponseEntity<UserDto> {
        val user =
            userRepository.findByUsernameIgnoreCase(loginRequest.username)
                ?: throw BadRequestException("Invalid credentials.")

        if (!passwordEncoder.matches(loginRequest.rawPassword, user.passwordHash)) {
            throw BadRequestException("Invalid credentials.")
        }

        // Initialize the security headers
        val accessCookie = cookieService.generateAccessCookie(user)
        val refreshCookie = cookieService.generateRefreshCookie(user)

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString())
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString())

        val userDto = UserMapper.toDto(user)

        return ResponseEntity(userDto, HttpStatus.OK)
    }

    /**
     * Retrieves the currently authenticated user details.
     *
     * If there is no authenticated user, throws a 401 status.
     */
    fun currentAuthUser(): ResponseEntity<UserDto> {
        val auth = SecurityContextHolder.getContext().authentication

        return if (auth != null && auth.isAuthenticated && auth.principal is CustomUserDetails) {
            val username = (auth.principal as CustomUserDetails).username
            val user =
                userRepository.findByUsernameIgnoreCase(username)
                    ?: throw NotFoundException(
                        "User not found when checking login status: $username",
                    )

            val userDto = UserMapper.toDto(user)

            ResponseEntity(userDto, HttpStatus.OK)
        } else {
            ResponseEntity(HttpStatus.UNAUTHORIZED)
        }
    }

    fun refreshSession(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Void> {
        // Retrieve the refresh token
        val refreshToken =
            request.cookies?.firstOrNull { it.name == CookieService.REFRESH_TOKEN_NAME }?.value
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        // Get username from the cookie
        val username =
            jwtService.extractUsername(refreshToken) ?: throw ForbiddenException("Invalid token.")

        // Load the user details
        val userDetails = userDetailsService.loadUserByUsername(username)

        // Validate the token
        if (!jwtService.validToken(refreshToken, userDetails)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        // Load user data
        val user =
            userRepository.findByUsernameIgnoreCase(username)
                ?: throw NotFoundException("User not found: $username")

        val newAccessTokenCookie = cookieService.generateAccessCookie(user)
        val newRefreshTokenCookie = cookieService.generateRefreshCookie(user)

        response.addHeader("Set-Cookie", newAccessTokenCookie.toString())
        response.addHeader("Set-Cookie", newRefreshTokenCookie.toString())

        return ResponseEntity.ok().build()
    }

    fun logoutUser(response: HttpServletResponse): ResponseEntity<Void> {
        cookieService.clearSessionCookies(response)

        return ResponseEntity.ok().build()
    }
}
