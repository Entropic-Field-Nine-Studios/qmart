package com.mattrition.qmart.auth.filters

import com.mattrition.qmart.auth.CookieService
import com.mattrition.qmart.auth.CustomUserDetailsService
import com.mattrition.qmart.auth.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userDetailsService: CustomUserDetailsService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val accessToken =
            request.cookies?.firstOrNull { it.name == CookieService.ACCESS_TOKEN_NAME }?.value

        accessToken?.let { token ->
            // Token was provided, proceed to authentication
            jwtService.extractUsername(token)?.let { username ->
                val userDetails = userDetailsService.loadUserByUsername(username)
                val auth =
                    UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)

                SecurityContextHolder.getContext().authentication = auth
            }
                ?: run {
                    response.sendError(
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "Invalid or expired token.",
                    )

                    return
                }
        }

        filterChain.doFilter(request, response)
    }
}
