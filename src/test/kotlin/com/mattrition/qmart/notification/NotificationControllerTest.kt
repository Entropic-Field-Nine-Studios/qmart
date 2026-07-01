package com.mattrition.qmart.notification

import com.mattrition.qmart.BaseH2Test
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.PATCH
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.OffsetDateTime

class NotificationControllerTest : BaseH2Test() {
    companion object {
        private const val BASE_PATH = "/api/notifications"
    }

    @Autowired lateinit var notificationService: NotificationService

    @Autowired lateinit var notificationRepo: NotificationRepository

    @BeforeEach
    fun beforeEach() {
        // Add two notifications
        notificationService.createNotification(
            userId = TestUsers.user.id!!,
            message = "Test Notification",
            route = "/home",
        )

        notificationService.createNotification(
            userId = TestUsers.user.id!!,
            message = "Test Notification 2",
            route = "/404",
        )

        // Add one for admin
        notificationService.createNotification(
            userId = TestUsers.admin.id!!,
            message = "Test Notification admin",
            route = "/dashboard",
        )
    }

    @Nested
    inner class GetNotifications {
        @Test
        fun `should return 403 forbidden when retrieving unowned notifications`() {
            mockRequest(
                requestType = GET,
                path = "$BASE_PATH/user/${TestUsers.user.id!!}",
                accessToken = null,
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should retrieve user notifications`() {
            mockRequest(
                requestType = GET,
                path = "$BASE_PATH/user/${TestUsers.user.id!!}",
                accessToken = TestAccessTokens.user,
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        }

        @Test
        fun `should not retrieve notifications that are inactive`() {
            val userNotifs = notificationRepo.findByUser(TestUsers.user.id!!)
            userNotifs.shouldNotBeEmpty()

            // Set user notifications to inactive
            userNotifs.forEach { notification ->
                notification.deletedAt = OffsetDateTime.now()

                notificationRepo.save(notification)
            }

            mockRequest(
                requestType = GET,
                path = "$BASE_PATH/user/${TestUsers.user.id!!}",
                accessToken = TestAccessTokens.user,
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }
    }

    @Nested
    inner class PatchNotifications {
        @Test
        fun `should return 403 forbidden when updating other user notifications`() {
            val sampleNotif = notificationRepo.findByUser(TestUsers.user.id!!).first()

            // Hiding it
            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/${sampleNotif.id}/hide",
                accessToken = null,
            ).andExpect(status().isForbidden)

            // Hiding all
            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/user/${TestUsers.user.id}/hideAll",
                accessToken = TestAccessTokens.superadmin,
            ).andExpect(status().isForbidden)

            // Reading it
            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/${sampleNotif.id}/read",
                accessToken = TestAccessTokens.admin,
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should hide all notifications`() {
            val userNotifs = notificationRepo.findByUser(TestUsers.user.id!!)
            userNotifs shouldHaveSize 2

            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/user/${TestUsers.user.id}/hideAll",
                accessToken = TestAccessTokens.user,
            ).andExpect(status().isOk)

            notificationRepo.findByUser(TestUsers.user.id!!).shouldBeEmpty()
        }

        @Test
        fun `should read notification`() {
            val adminNotifs = notificationRepo.findByUser(TestUsers.admin.id!!)
            adminNotifs shouldHaveSize 1

            val adminNotif = adminNotifs.first()
            adminNotif.readAt.shouldBeNull()

            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/${adminNotif.id}/read",
                accessToken = TestAccessTokens.admin,
            ).andExpect(status().isOk)

            val notif = notificationRepo.findById(adminNotif.id!!).get()
            notif.readAt.shouldNotBeNull()
        }

        @Test
        fun `should hide notification`() {
            val adminNotifs = notificationRepo.findByUser(TestUsers.admin.id!!)
            adminNotifs shouldHaveSize 1

            val adminNotif = adminNotifs.first()
            adminNotif.deletedAt.shouldBeNull()

            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/${adminNotif.id}/hide",
                accessToken = TestAccessTokens.admin,
            ).andExpect(status().isOk)

            val notif = notificationRepo.findById(adminNotif.id!!).get()
            notif.deletedAt.shouldNotBeNull()
        }
    }
}
