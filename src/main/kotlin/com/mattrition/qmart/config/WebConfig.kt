package com.mattrition.qmart.config

import com.mattrition.qmart.config.properties.CorsProps
import com.mattrition.qmart.config.properties.ImageUploadProps
import com.mattrition.qmart.logging.RequestLoggingInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Overrides web access to avoid having to allow CORS on each web controller. */
@Configuration
class WebConfig(
    private val interceptor: RequestLoggingInterceptor,
    private val imageProps: ImageUploadProps,
    private val corsProps: CorsProps,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry
            .addMapping("/**")
            .allowedOriginPatterns(*corsProps.allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry
            .addResourceHandler("/uploads/**")
            .addResourceLocations("file:${imageProps.uploadDir}/")
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(interceptor)
    }
}
