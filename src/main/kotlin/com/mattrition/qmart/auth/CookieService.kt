package com.mattrition.qmart.auth

import com.mattrition.qmart.user.User
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.server.Cookie
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@Service
class CookieService(
    private val jwtService: JwtService,
) {
    companion object {
        private val accessTokenAge = 15.minutes
        private val refreshTokenAge = 30.days

        private val cookieSameSitePolicy = Cookie.SameSite.STRICT.attributeValue()

        const val ACCESS_TOKEN_NAME = "access_token"
        const val REFRESH_TOKEN_NAME = "refresh_token"
    }

    /** Generates an access token cookie for a user. */
    fun generateAccessCookie(user: User): ResponseCookie {
        val accessToken =
            jwtService.generateToken(
                username = user.username,
                id = user.id!!,
                role = user.role,
                expirationMillis = accessTokenAge.inWholeMilliseconds,
            )

        return buildAccessCookie(accessToken)
    }

    /** Generates a refresh token cookie for a user. */
    fun generateRefreshCookie(user: User): ResponseCookie {
        val refreshToken =
            jwtService.generateToken(
                username = user.username,
                id = user.id!!,
                role = user.role,
                expirationMillis = refreshTokenAge.inWholeMilliseconds,
            )

        return buildRefreshCookie(refreshToken)
    }

    /** Removes authenticated cookies from a response. */
    fun clearSessionCookies(response: HttpServletResponse) {
        val clearedAccessCookie =
            ResponseCookie
                .from(ACCESS_TOKEN_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite(cookieSameSitePolicy)
                .path("/")
                .maxAge(0)
                .build()

        val clearedRefreshCookie =
            ResponseCookie
                .from(REFRESH_TOKEN_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite(cookieSameSitePolicy)
                .path("/api/auth/refresh")
                .maxAge(0)
                .build()

        response.addHeader(HttpHeaders.SET_COOKIE, clearedAccessCookie.toString())
        response.addHeader(HttpHeaders.SET_COOKIE, clearedRefreshCookie.toString())
    }

    private fun buildAccessCookie(token: String) =
        ResponseCookie
            .from(ACCESS_TOKEN_NAME, token)
            .httpOnly(true)
            .secure(true)
            .sameSite(cookieSameSitePolicy)
            .path("/")
            .maxAge(accessTokenAge.inWholeSeconds)
            .build()

    private fun buildRefreshCookie(token: String) =
        ResponseCookie
            .from(REFRESH_TOKEN_NAME, token)
            .httpOnly(true)
            .secure(true)
            .sameSite(cookieSameSitePolicy)
            .path("/api/auth/refresh")
            .maxAge(refreshTokenAge.inWholeSeconds)
            .build()
}
