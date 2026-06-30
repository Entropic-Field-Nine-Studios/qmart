package com.mattrition.qmart.auth.filters

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/** Prevents bulk requests from accessing the login endpoint. */
@Component
class LoginRateLimitFilter : OncePerRequestFilter() {
    private val maxAttempts = 5
    private val windowMs = 60.seconds.inWholeMilliseconds

    private val attempts = ConcurrentHashMap<String, LoginAttempt>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestingLogin =
            request.requestURI.contains("/login") && request.method == HttpMethod.POST.name()

        if (requestingLogin) {
            val ip = request.remoteAddr
            val now = System.currentTimeMillis()

            val attempt =
                attempts.compute(ip) { _, existing ->
                    val current = existing ?: LoginAttempt(now, 0)

                    if (now - current.startTime > windowMs) {
                        LoginAttempt(now, 1)
                    } else {
                        current.copy(count = current.count + 1)
                    }
                }!!

            if (attempt.count > maxAttempts) {
                response.status = HttpStatus.TOO_MANY_REQUESTS.value()
                response.writer.write("Too many login attempts. Try again later.")
                return
            }
        }

        filterChain.doFilter(request, response)
    }

    data class LoginAttempt(
        val startTime: Long,
        val count: Int,
    )
}
