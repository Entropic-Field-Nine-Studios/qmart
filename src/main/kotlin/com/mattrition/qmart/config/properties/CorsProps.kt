package com.mattrition.qmart.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cors")
data class CorsProps(
    val allowedOrigins: List<String> = emptyList(),
)
