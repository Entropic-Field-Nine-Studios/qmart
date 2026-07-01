package com.mattrition.qmart.order

import com.mattrition.qmart.BaseH2Test
import com.mattrition.qmart.cart.CartItem
import com.mattrition.qmart.cart.CartItemRepository
import com.mattrition.qmart.itemlisting.ItemListing
import com.mattrition.qmart.notification.NotificationRepository
import com.mattrition.qmart.order.dto.CreateOrderRequestDto
import com.mattrition.qmart.order.dto.OrderDto
import com.mattrition.qmart.orderitem.OrderItemRepository
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.util.UUID

class OrderControllerTest : BaseH2Test() {
    companion object {
        private const val BASE_PATH = "/api/orders"
    }

    @Autowired lateinit var orderRepository: OrderRepository

    @Autowired lateinit var cartItemRepository: CartItemRepository

    @Autowired lateinit var notificationRepository: NotificationRepository

    @Autowired lateinit var orderItemRepository: OrderItemRepository

    @Autowired lateinit var orderService: OrderService

    private lateinit var listingMod: ItemListing
    private lateinit var listingAdmin: ItemListing

    private val guestId = UUID.randomUUID()

    /** Pre-fills a create order request with shipping information. */
    private fun genCreateOrder(
        buyerId: UUID? = null,
        guestSessionId: UUID? = null,
        guestEmail: String? = null,
        totalPaid: BigDecimal = BigDecimal("1.00"),
    ) = CreateOrderRequestDto(
        buyerId = buyerId,
        guestSessionId = guestSessionId,
        guestEmail = guestEmail,
        totalPaid = totalPaid,
        shippingFirstname = "Test",
        shippingLastname = "Last",
        shippingAddress1 = "123 street",
        shippingAddress2 = null,
        shippingCity = "New York",
        shippingState = "New York",
        shippingZip = "55555",
        shippingPhone = "555-555-5555",
    )

    @BeforeEach
    fun init() {
        val listings = super.initListings()

        listingMod = listings.first()
        listingAdmin = listings.last()

        // Add items to user's cart
        val cartItem1 =
            CartItem(userId = TestUsers.user.id!!, listingId = listingMod.id!!, quantity = 1)

        val cartItem2 =
            CartItem(userId = TestUsers.user.id!!, listingId = listingAdmin.id!!, quantity = 1)

        // Guest cart item
        val guestCartItem =
            CartItem(
                userId = null,
                guestSessionId = guestId,
                listingId = listingMod.id!!,
                quantity = 1,
            )

        cartItemRepository.saveAll(listOf(guestCartItem, cartItem1, cartItem2))
    }

    @Nested
    inner class CreateOrders {
        @Test
        fun `should return 403 forbidden when creating an order using mismatched token`() {
            val req = genCreateOrder(buyerId = TestUsers.user.id!!)

            mockRequest(
                requestType = POST,
                path = BASE_PATH,
                accessToken = TestAccessTokens.superadmin,
                body = req,
            ).andExpect(status().isForbidden)

            // Non-authenticated request
            mockRequest(requestType = POST, path = BASE_PATH, accessToken = null, body = req)
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should create order for user`() {
            val req = genCreateOrder(buyerId = TestUsers.user.id!!)

            mockRequest(
                requestType = POST,
                path = BASE_PATH,
                accessToken = TestAccessTokens.user,
                body = req,
            ).andExpect(status().isCreated)
        }

        @Test
        fun `should create order for non-user`() {
            val req =
                genCreateOrder(
                    buyerId = null,
                    guestSessionId = guestId,
                    guestEmail = "test@email.com",
                )

            mockRequest(requestType = POST, path = BASE_PATH, accessToken = null, body = req)
                .andExpect(status().isCreated)
        }

        @Test
        fun `should return 400 bad request with conflicting ownership`() {
            val noOwnerReq =
                genCreateOrder(
                    buyerId = null,
                    guestSessionId = null,
                    guestEmail = "test@email.com", // should not matter
                )

            mockRequest(requestType = POST, path = BASE_PATH, accessToken = null, body = noOwnerReq)
                .andExpect(status().isBadRequest)

            val bothOwnerReq =
                genCreateOrder(
                    buyerId = TestUsers.user.id!!,
                    guestSessionId = guestId,
                    guestEmail = "test@email.com", // should not matter
                )

            mockRequest(
                requestType = POST,
                path = BASE_PATH,
                accessToken = TestAccessTokens.user,
                body = bothOwnerReq,
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 bad request with non-user origin without guest email`() {
            val req = genCreateOrder(buyerId = null, guestSessionId = guestId, guestEmail = null)

            mockRequest(requestType = POST, path = BASE_PATH, accessToken = null, body = req)
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 bad request on invalid total paid values`() {
            val invalidValues = listOf(BigDecimal("-1.0"), BigDecimal("-9999.0"), BigDecimal("0.0"))

            invalidValues.forEach { value ->
                val req =
                    genCreateOrder(
                        buyerId = TestUsers.user.id!!,
                        guestSessionId = null,
                        guestEmail = null,
                        totalPaid = value,
                    )

                mockRequest(
                    requestType = POST,
                    path = BASE_PATH,
                    accessToken = TestAccessTokens.user,
                    body = req,
                ).andExpect(status().isBadRequest)
            }
        }

        @Test
        fun `should return 400 bad request when providing guest email as user`() {
            val req =
                genCreateOrder(
                    buyerId = TestUsers.user.id!!,
                    guestSessionId = null,
                    guestEmail = "",
                )

            mockRequest(
                requestType = POST,
                path = BASE_PATH,
                accessToken = TestAccessTokens.user,
                body = req,
            ).andExpect(status().isBadRequest)
        }

        @Nested
        inner class IntegrityTests {
            private val userRequest =
                genCreateOrder(buyerId = TestUsers.user.id!!, totalPaid = BigDecimal("300.0"))

            @Test
            fun `should deduct balance from user`() {
                val prevBalance = TestUsers.user.balance
                prevBalance shouldBeGreaterThanOrEqualTo BigDecimal("300.0")

                mockRequest(
                    requestType = POST,
                    path = BASE_PATH,
                    accessToken = TestAccessTokens.user,
                    body = userRequest,
                ).andExpect(status().isCreated)

                val expectedBalance = prevBalance - BigDecimal("300.00")
                val user = userRepository.findById(TestUsers.user.id!!).get()
                user.balance shouldBe expectedBalance
            }

            @Test
            fun `should clear buyer's cart items`() {
                val prevItems = cartItemRepository.findCartItemsByUserId(TestUsers.user.id!!)
                prevItems shouldHaveSize 2

                mockRequest(
                    requestType = POST,
                    path = BASE_PATH,
                    accessToken = TestAccessTokens.user,
                    body = userRequest,
                ).andExpect(status().isCreated)

                val userItems = cartItemRepository.findCartItemsByUserId(TestUsers.user.id!!)
                userItems shouldHaveSize 0
            }

            @Test
            fun `should send notification to sellers`() {
                val prevNotifsMod = notificationRepository.findByUser(TestUsers.moderator.id!!)
                prevNotifsMod shouldHaveSize 0

                val prevNotifsAdmin = notificationRepository.findByUser(TestUsers.admin.id!!)
                prevNotifsAdmin shouldHaveSize 0

                mockRequest(
                    requestType = POST,
                    path = BASE_PATH,
                    accessToken = TestAccessTokens.user,
                    body = userRequest,
                ).andExpect(status().isCreated)

                val modNotifs = notificationRepository.findByUser(TestUsers.moderator.id!!)
                modNotifs shouldHaveSize 1

                val adminNotifs = notificationRepository.findByUser(TestUsers.admin.id!!)
                adminNotifs shouldHaveSize 1
            }

            @Test
            fun `should create order items based on items in cart`() {
                mockRequest(
                    requestType = POST,
                    path = BASE_PATH,
                    accessToken = TestAccessTokens.user,
                    body = userRequest,
                ).andExpect(status().isCreated)

                val userOrders = orderRepository.findOrdersByBuyerId(TestUsers.user.id!!)
                userOrders shouldHaveSize 1

                val userOrder = userOrders.first()
                val orderItems = orderItemRepository.findOrderItemsByOrderId(userOrder.id!!)
                orderItems shouldHaveSize 2
                orderItems.forOne { it.listingId shouldBe listingMod.id }
                orderItems.forOne { it.listingId shouldBe listingAdmin.id }
            }
        }
    }

    @Nested
    inner class GetOrders {
        @BeforeEach
        fun init() {
            val userRequest =
                genCreateOrder(
                    buyerId = TestUsers.user.id!!,
                    guestSessionId = null,
                    guestEmail = null,
                )

            authenticate(TestUsers.user)

            orderService.createOrder(userRequest)

            val guestRequest =
                genCreateOrder(guestSessionId = guestId, guestEmail = "email@test.com")

            clearAuth()

            orderService.createOrder(guestRequest)
        }

        @Test
        fun `should get orders by user`() {
            mockRequest(
                requestType = GET,
                path = "$BASE_PATH/user/${TestUsers.user.id}",
                accessToken = TestAccessTokens.user,
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
        }

        @Test
        fun `should get orders for seller with relevant items only`() {
            val result =
                mockRequest(
                    requestType = GET,
                    path = "$BASE_PATH/seller/${TestUsers.moderator.id}",
                    accessToken = TestAccessTokens.moderator,
                    params = mapOf("unfinished" to "true"),
                ).andExpect(status().isOk)
                    .andReturn()

            val body = result.response.contentAsString
            val orders = objectMapper.readValue<List<OrderDto>>(body)
            orders shouldHaveSize 2

            // Verify order from user
            orders.forOne { o ->
                o.buyerId.shouldNotBeNull() shouldBe TestUsers.user.id!!
                o.guestEmail.shouldBeNull()
            }

            // Verify order from guest
            orders.forOne { o ->
                o.buyerId.shouldBeNull()
                o.guestEmail.shouldNotBeNull()
            }

            // Verify all order items are only for the seller
            orders.forEach { order ->
                order.orderItems.forAll { it.sellerId shouldBe TestUsers.moderator.id }
            }
        }
    }
}
