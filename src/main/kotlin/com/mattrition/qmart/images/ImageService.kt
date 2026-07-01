package com.mattrition.qmart.images

import com.mattrition.qmart.config.properties.ImageUploadProps
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

@Service
class ImageService(
    private val imageProps: ImageUploadProps,
    private val s3Client: S3Client,
) {
    private val uploadDir: Path by lazy {
        val dir = Paths.get(imageProps.uploadDir!!)

        if (!Files.exists(dir)) {
            Files.createDirectories(dir)
        }

        dir
    }

    fun saveImage(file: MultipartFile): String =
        when (imageProps.provider) {
            ImageProvider.LOCAL -> saveToLocal(file)
            ImageProvider.S3 -> saveToS3(file)
        }

    private fun saveToLocal(file: MultipartFile): String {
        val filename = "${UUID.randomUUID()}-${file.originalFilename}"
        val target = uploadDir.resolve(filename)

        Files.copy(file.inputStream, target)

        return "${imageProps.baseUrl}/uploads/$filename"
    }

    private fun saveToS3(file: MultipartFile): String {
        val filename = "${UUID.randomUUID()}-${file.originalFilename}"

        val putObjectRequest =
            PutObjectRequest
                .builder()
                .bucket(imageProps.bucket)
                .key(filename)
                .contentType(file.contentType)
                .build()

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.bytes))

        return "${imageProps.baseUrl}/$filename"
    }
}
