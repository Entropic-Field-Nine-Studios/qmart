package com.mattrition.qmart.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

@Configuration
class AwsConfig {
    @Bean fun s3Client(): S3Client = S3Client.builder().region(Region.US_EAST_1).build()
}
