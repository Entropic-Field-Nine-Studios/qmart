package com.mattrition.qmart.cart

import com.mattrition.qmart.BaseH2Test
import com.mattrition.qmart.cart.dto.AddCartItemDto
import com.mattrition.qmart.itemlisting.ItemListing
import com.mattrition.qmart.itemlisting.dto.ItemListingDto
import com.mattrition.qmart.itemlisting.dto.ItemListingMapper
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.DELETE
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

class CartItemControllerTest : BaseH2Test() {
    companion object {
        const val BASE_PATH = "/api/cart-items"
    }

    @Autowired lateinit var cartItemRepository: CartItemRepository

    private lateinit var listing1: ItemListing
    private lateinit var listing1Dto: ItemListingDto
    private lateinit var listing2: ItemListing
    private lateinit var listing2Dto: ItemListingDto

    private lateinit var cartItem1: CartItem
    private lateinit var cartItem2: CartItem

    private val guestId = UUID.randomUUID()

    private lateinit var guestCartItem: CartItem

    @BeforeEach
    fun addCartItems() {
        val listings = super.initListings()
        listing1 = listings.last()
        listing1Dto =
            ItemListingMapper.toDto(listing1, TestUsers.admin.username, listing1.category()!!)

        listing2 = listings.first()
        listing2Dto =
            ItemListingMapper.toDto(listing2, TestUsers.moderator.username, listing2.category()!!)

        val item1 = CartItem(userId = TestUsers.user.id!!, listingId = listing1.id!!, quantity = 1)
        val item2 = CartItem(userId = TestUsers.user.id!!, listingId = listing2.id!!, quantity = 1)

        cartItem1 = cartItemRepository.save(item1)
        cartItem2 = cartItemRepository.save(item2)

        val guestItem =
            CartItem(
                userId = null,
                guestSessionId = guestId,
                listingId = listing1.id!!,
                quantity = 1,
            )

        guestCartItem = cartItemRepository.save(guestItem)
    }

    @Nested
    inner class AddItem {
        @Test
        fun `adding item to other cart returns forbidden 403`() {
            val req =
                AddCartItemDto(
                    userId = TestUsers.user.id!!,
                    guestSessionId = null,
                    listingInfo = listing1Dto,
                    itemQuantity = 1,
                )

            mockRequest(
                requestType = POST,
                path = BASE_PATH,
                accessToken = TestAccessTokens.admin,
                body = req,
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should add item to cart`() {
            cartItemRepository.findCartItemsByUserId(TestUsers.superadmin.id!!).size shouldBe 0

            val req =
                AddCartItemDto(
                    userId = TestUsers.superadmin.id!!,
                    guestSessionId = null,
                    listingInfo = listing1Dto,
                    itemQuantity = 1,
                )

            mockRequest(
                requestType = POST,
                path = BASE_PATH,
                accessToken = TestAccessTokens.superadmin,
                body = req,
            ).andExpect(status().isOk)

            val userItems = cartItemRepository.findCartItemsByUserId(TestUsers.superadmin.id!!)
            userItems.size shouldBe 1
            userItems.first().listingId shouldBe listing1.id
        }

        @Test
        fun `user should not be able to add owned items to cart`() {
            val ogItem = itemListingRepository.findById(listing1.id!!).getOrNull().shouldNotBeNull()
            val listingSellerId = ogItem.sellerId.shouldNotBeNull()
            listingSellerId shouldBeEqual TestUsers.admin.id!!
            listing1Dto.sellerId shouldBeEqual TestUsers.admin.id!!

            val req =
                AddCartItemDto(
                    userId = listingSellerId,
                    guestSessionId = null,
                    listingInfo = listing1Dto,
                    itemQuantity = 1,
                )

            mockRequest(
                requestType = POST,
                path = BASE_PATH,
                accessToken = TestAccessTokens.admin,
                body = req,
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 400 bad request on conflicting ownership`() {
            val noRequest =
                AddCartItemDto(
                    userId = null,
                    guestSessionId = null,
                    listingInfo = listing1Dto,
                    itemQuantity = 1,
                )

            mockRequest(requestType = POST, path = BASE_PATH, accessToken = null, body = noRequest)
                .andExpect(status().isBadRequest)

            val bothReq =
                AddCartItemDto(
                    userId = TestUsers.user.id!!,
                    guestSessionId = guestId,
                    listingInfo = listing1Dto,
                    itemQuantity = 1,
                )

            mockRequest(
                requestType = POST,
                path = BASE_PATH,
                accessToken = TestAccessTokens.user,
                body = bothReq,
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should add item to guest cart`() {
            val preItems = cartItemRepository.findGuestCartItems(guestId)
            preItems shouldHaveSize 1
            preItems.forNone { it.listingId shouldBe listing2Dto.id }

            val req =
                AddCartItemDto(
                    userId = null,
                    guestSessionId = guestId,
                    listingInfo = listing2Dto,
                    itemQuantity = 1,
                )

            mockRequest(requestType = POST, path = BASE_PATH, accessToken = null, body = req)
                .andExpect(status().isOk)

            val guestItems = cartItemRepository.findGuestCartItems(guestId)
            guestItems shouldHaveSize 2
            guestItems.forOne { it.listingId shouldBe listing2Dto.id }
        }

        @Test
        fun `should increment quantity of item in guest cart`() {
            val req =
                AddCartItemDto(
                    userId = null,
                    guestSessionId = guestId,
                    listingInfo = listing1Dto,
                    itemQuantity = 1,
                )

            mockRequest(requestType = POST, path = BASE_PATH, accessToken = null, body = req)
                .andExpect(status().isOk)

            val guestItems = cartItemRepository.findGuestCartItems(guestId)
            guestItems shouldHaveSize 1

            val guestItem = guestItems.first()
            guestItem.quantity shouldBe 2
            guestItem.updatedAt shouldBeGreaterThan guestItem.createdAt
        }
    }

    @Nested
    inner class RemoveItems {
        @Test
        fun `clearing items from another cart should return forbidden 403`() {
            mockRequest(
                requestType = DELETE,
                path = "$BASE_PATH/user/${TestUsers.user.id}",
                accessToken = TestAccessTokens.superadmin,
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should clear all cart items`() {
            cartItemRepository.findCartItemsByUserId(TestUsers.user.id!!).size shouldBeGreaterThan 0

            mockRequest(
                requestType = DELETE,
                path = "$BASE_PATH/user/${TestUsers.user.id}",
                accessToken = TestAccessTokens.user,
            ).andExpect(status().isOk)

            cartItemRepository.findCartItemsByUserId(TestUsers.user.id!!).size shouldBe 0
        }

        @Test
        fun `removing an item from another cart should return forbidden 403`() {
            val routePath = "$BASE_PATH/user/${TestUsers.user.id}/listing/${listing1.id}"
            mockRequest(
                requestType = DELETE,
                path = routePath,
                accessToken = TestAccessTokens.superadmin,
            ).andExpect(status().isForbidden)

            mockRequest(requestType = DELETE, path = routePath, accessToken = null)
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should remove an item from cart`() {
            cartItemRepository.findById(cartItem1.id!!).getOrNull().shouldNotBeNull()

            mockRequest(
                requestType = DELETE,
                path = "$BASE_PATH/user/${TestUsers.user.id}/listing/${listing1.id}",
                accessToken = TestAccessTokens.user,
            ).andExpect(status().isOk)

            cartItemRepository.findById(cartItem1.id!!).getOrNull().shouldBeNull()
            cartItemRepository.findCartItemsByUserId(TestUsers.user.id!!).size shouldBe 1
        }

        @Test
        fun `should remove all items from guest cart`() {
            val preItems = cartItemRepository.findGuestCartItems(guestId)
            preItems shouldHaveAtLeastSize 1

            mockRequest(
                requestType = DELETE,
                path = "$BASE_PATH/guest/$guestId",
                accessToken = null,
            ).andExpect(status().isOk)

            val guestItems = cartItemRepository.findGuestCartItems(guestId)
            guestItems shouldHaveSize 0
        }

        @Test
        fun `should remove item from guest cart`() {
            // Add second item to guest cart
            val createReq =
                AddCartItemDto(
                    userId = null,
                    guestSessionId = guestId,
                    listingInfo = listing2Dto,
                    itemQuantity = 1,
                )

            mockRequest(requestType = POST, path = BASE_PATH, accessToken = null, body = createReq)
                .andExpect(status().isOk)

            val preItems = cartItemRepository.findGuestCartItems(guestId)
            preItems shouldHaveSize 2
            preItems.forOne { it.listingId shouldBe listing2Dto.id }

            mockRequest(
                requestType = DELETE,
                path = "$BASE_PATH/guest/$guestId/listing/${listing2Dto.id}",
                accessToken = null,
            ).andExpect(status().isOk)

            val guestItems = cartItemRepository.findGuestCartItems(guestId)
            guestItems shouldHaveSize 1
            guestItems.forNone { it.listingId shouldBe listing2Dto.id }
        }
    }

    @Nested
    inner class GetCartItems {
        @Test
        fun `getting items from another cart should return forbidden 403`() {
            mockRequest(
                requestType = GET,
                path = "$BASE_PATH/user/${TestUsers.user.id}",
                accessToken = TestAccessTokens.superadmin,
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should get items from a users cart`() {
            mockRequest(
                requestType = GET,
                path = "$BASE_PATH/user/${TestUsers.user.id}",
                accessToken = TestAccessTokens.user,
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        }

        @Test
        fun `should get items from guest cart`() {
            mockRequest(requestType = GET, path = "$BASE_PATH/guest/$guestId", accessToken = null)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
        }
    }
}
