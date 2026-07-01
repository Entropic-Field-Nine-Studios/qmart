package com.mattrition.qmart.config.properties

import com.mattrition.qmart.images.ImageProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "images")
class ImageUploadProps {
    lateinit var provider: ImageProvider

    var uploadDir: String? = null
    var bucket: String? = null
    var baseUrl: String? = null
}
