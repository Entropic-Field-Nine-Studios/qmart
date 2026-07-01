package com.mattrition.qmart.orderitems

import com.mattrition.qmart.BaseH2Test
import com.mattrition.qmart.cart.CartItem
import com.mattrition.qmart.cart.CartItemRepository
import com.mattrition.qmart.order.OrderService
import com.mattrition.qmart.order.dto.CreateOrderRequestDto
import com.mattrition.qmart.order.dto.OrderDto
import com.mattrition.qmart.orderitem.OrderItemRepository
import com.mattrition.qmart.orderitem.OrderItemStatus
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.PATCH
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.jvm.optionals.getOrNull

class OrderItemControllerTest : BaseH2Test() {
    companion object {
        private const val BASE_PATH = "/api/order-items"
    }

    @Autowired lateinit var orderItemRepository: OrderItemRepository

    @Autowired lateinit var cartItemRepository: CartItemRepository

    @Autowired lateinit var orderService: OrderService

    private lateinit var order: OrderDto

    @BeforeEach
    fun beforeEach() {
        val listings = super.initListings()

        val cartItem =
            CartItem(userId = TestUsers.user.id!!, listingId = listings.first().id!!, quantity = 1)
        cartItemRepository.save(cartItem)

        val createReq =
            CreateOrderRequestDto(
                buyerId = TestUsers.user.id!!,
                guestSessionId = null,
                guestEmail = null,
                totalPaid = BigDecimal(100),
                shippingFirstname = "Test1",
                shippingLastname = "Test2",
                shippingAddress1 = "1234 Main St",
                shippingAddress2 = null,
                shippingCity = "London",
                shippingState = "California",
                shippingZip = "11111",
                shippingPhone = "555-555-5555",
            )

        authenticate(TestUsers.user)

        order = orderService.createOrder(createReq)
    }

    @Nested
    inner class PatchOrderItem {
        @Test
        fun `non-seller patching order item should return 403 forbidden`() {
            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/${order.orderItems.first().id!!}",
                accessToken = TestAccessTokens.user,
                params = mapOf("newStatus" to OrderItemStatus.SHIPPED.toString()),
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should set shipping date and status`() {
            orderItem().status shouldBe OrderItemStatus.PAID_PENDING_SHIPMENT
            orderItem().shippedOn.shouldBeNull()

            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/${orderItem().id!!}?newStatus=${OrderItemStatus.SHIPPED}",
                accessToken = TestAccessTokens.moderator,
            ).andExpect(status().isOk)

            orderItem().shippedOn.shouldNotBeNull() shouldBeLessThanOrEqualTo OffsetDateTime.now()
            orderItem().status shouldBe OrderItemStatus.SHIPPED
        }

        @Test
        fun `canceling order item should not set shipped on date`() {
            orderItem().status shouldBe OrderItemStatus.PAID_PENDING_SHIPMENT
            orderItem().shippedOn.shouldBeNull()

            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/${orderItem().id!!}?newStatus=${OrderItemStatus.CANCELED}",
                accessToken = TestAccessTokens.moderator,
            ).andExpect(status().isOk)

            orderItem().status shouldBe OrderItemStatus.CANCELED
            orderItem().shippedOn.shouldBeNull()
        }

        @Test
        fun `seller changing status from modified status should return 403 forbidden`() {
            for (currentStatus in OrderItemStatus.entries) {
                if (currentStatus != OrderItemStatus.PAID_PENDING_SHIPMENT) {
                    orderItem().status = currentStatus

                    for (newStatus in OrderItemStatus.entries) {
                        mockRequest(
                            requestType = PATCH,
                            path = "$BASE_PATH/${orderItem().id!!}?newStatus=$newStatus",
                            accessToken = TestAccessTokens.moderator,
                        ).andExpect(status().isForbidden)

                        orderItem().status shouldBe currentStatus
                    }
                }
            }
        }

        @Test
        fun `seller changing status to restricted value should return 403 forbidden`() {
            val allowed = listOf(OrderItemStatus.SHIPPED, OrderItemStatus.CANCELED)
            orderItem().status = OrderItemStatus.PAID_PENDING_SHIPMENT

            for (newStatus in OrderItemStatus.entries) {
                if (newStatus !in allowed) {
                    mockRequest(
                        requestType = PATCH,
                        path = "$BASE_PATH/${orderItem().id!!}?newStatus=$newStatus",
                        accessToken = TestAccessTokens.moderator,
                    ).andExpect(status().isForbidden)

                    orderItem().status shouldBe OrderItemStatus.PAID_PENDING_SHIPMENT
                    orderItem().shippedOn.shouldBeNull()
                }
            }
        }

        private fun orderItem() =
            orderItemRepository
                .findById(order.orderItems.first().id!!)
                .getOrNull()
                .shouldNotBeNull()
    }
}
