package com.mattrition.qmart.logging

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.lang.Exception
import java.util.UUID

@Component
class RequestLoggingInterceptor : HandlerInterceptor {
    private val log = LoggerFactory.getLogger(RequestLoggingInterceptor::class.java)

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        request.setAttribute("startTime", System.currentTimeMillis())

        val requestId = UUID.randomUUID().toString().substring(0, 8)
        request.setAttribute("requestId", requestId)

        val auth = SecurityContextHolder.getContext().authentication
        val username =
            if (auth != null && auth.isAuthenticated && auth !is AnonymousAuthenticationToken) {
                auth.name
            } else {
                "(non-user)"
            }

        request.setAttribute("username", username)

        val builder = StringBuilder("-> [REQUEST:  $requestId]\t\t\t\t= { ")

        builder.append("from: \"$username\", ")
        builder.append("method: \"${request.method}\", ")
        builder.append("uri: \"${request.requestURI}\"")
        builder.append(" }")

        log.debug(builder.toString())
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val start = request.getAttribute("startTime") as Long
        val duration = System.currentTimeMillis() - start

        val username = request.getAttribute("username") as String
        val requestId = request.getAttribute("requestId") as String

        val isUnhandledError = HttpStatus.valueOf(response.status).is5xxServerError

        if (ex != null && isUnhandledError) {
            log.error("Unhandled exception occurred during request [$requestId]", ex)
        }

        val builder = StringBuilder("<- [RESPONSE: $requestId] (${duration}ms)\t\t= { ")

        builder.append("to: \"$username\", ")
        builder.append("status: ${response.status}, ")
        builder.append("uri: \"${request.requestURI}\"")
        builder.append(" }")

        log.debug(builder.toString())
    }
}
