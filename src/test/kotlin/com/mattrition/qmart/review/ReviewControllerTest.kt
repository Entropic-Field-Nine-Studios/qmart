package com.mattrition.qmart.review

import com.mattrition.qmart.BaseH2Test
import com.mattrition.qmart.itemlisting.ItemListing
import com.mattrition.qmart.review.dto.CreateReviewRequest
import com.mattrition.qmart.review.dto.EditReviewRequest
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.everyItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.DELETE
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.PATCH
import org.springframework.http.HttpMethod.POST
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

class ReviewControllerTest : BaseH2Test() {
    companion object {
        private const val BASE_PATH = "/api/reviews"
    }

    @Autowired lateinit var reviewRepository: ReviewRepository

    private lateinit var sampleListing1: ItemListing
    private lateinit var sampleListing2: ItemListing

    @BeforeEach
    fun initListing() {
        val listings = super.initListings()

        sampleListing1 = listings.first()
        sampleListing2 = listings.last()
    }

    // Intentionally left at the top to test for other test classes
    @Nested
    inner class CreateReviews {
        @Test
        fun `should create review and update listing's review details`() {
            val listing1 = itemListingRepository.findById(sampleListing1.id!!).get()
            listing1.reviewCount shouldBe 0
            listing1.averageScore shouldBe BigDecimal("0.0")

            mockRequest(
                requestType = POST,
                path = "$BASE_PATH/listing/${listing1.id}",
                accessToken = TestAccessTokens.user,
                body = CreateReviewRequest(body = "New Review", score = 5),
            ).andExpect(status().isCreated)

            val updatedListing = itemListingRepository.findById(sampleListing1.id!!).get()
            updatedListing.reviewCount shouldBe 1
            updatedListing.averageScore shouldBe BigDecimal("5.0")

            val reviews = reviewRepository.findReviewsByListingId(sampleListing1.id!!)
            reviews shouldHaveSize 1
            reviews.first().user.id shouldBe TestUsers.user.id
        }

        @Test
        fun `should return 403 forbidden on unauthenticated request`() {
            val request = CreateReviewRequest(body = "New Review", score = 2)

            mockRequest(
                requestType = POST,
                path = "$BASE_PATH/listing/${sampleListing1.id}",
                accessToken = null,
                body = request,
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 403 forbidden when leaving a review on owned listing`() {
            // Pre-test: ensure "has existing" review rule is not being enforced
            val reviews = reviewRepository.findReviewsByListingId(sampleListing1.id!!)
            reviews.forNone { it.user.id shouldBe TestUsers.moderator.id }

            val request = CreateReviewRequest(body = "New Review", score = 5)

            mockRequest(
                requestType = POST,
                path = "$BASE_PATH/listing/${sampleListing1.id}",
                accessToken = TestAccessTokens.moderator,
                body = request,
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 403 forbidden when leaving a second review`() {
            initReviews()

            // Ensure user has one review already
            val reviews = reviewRepository.findReviewsByListingId(sampleListing1.id!!)
            reviews.forOne { it.user.id shouldBe TestUsers.user.id }

            mockRequest(
                requestType = POST,
                path = "$BASE_PATH/listing/${sampleListing1.id}",
                accessToken = TestAccessTokens.user,
                body = CreateReviewRequest(body = "Second review", score = 5),
            ).andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class GetReviews {
        @BeforeEach
        fun addReviews() {
            initReviews()
        }

        @Test
        fun `should get listing reviews`() {
            mockRequest(
                requestType = GET,
                path = "$BASE_PATH/listing/${sampleListing1.id}",
                accessToken = null,
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(TestUsers.admin.id.toString()))
                .andExpect(jsonPath("$[1].userId").value(TestUsers.user.id.toString()))
        }

        @Test
        fun `should get reviews by user`() {
            mockRequest(
                requestType = GET,
                path = "$BASE_PATH/user/${TestUsers.user.id}",
                accessToken = null,
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(
                    jsonPath("$[*].userId", everyItem(equalTo(TestUsers.user.id.toString()))),
                )
        }
    }

    @Nested
    inner class PatchReviews {
        private lateinit var userReview: Review

        @BeforeEach
        fun addReviews() {
            initReviews()

            userReview = reviewRepository.findReviewsByUserId(TestUsers.user.id!!).last()
        }

        @Test
        fun `should return 403 forbidden on mismatch user ID`() {
            val editBody = EditReviewRequest(newBody = "New body", newScore = 1)

            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/${userReview.id}",
                accessToken = null,
                body = editBody,
            ).andExpect(status().isForbidden)

            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/${userReview.id}",
                accessToken = TestAccessTokens.superadmin,
                body = editBody,
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 400 bad request on invalid score`() {
            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/${userReview.id}",
                accessToken = TestAccessTokens.user,
                body = EditReviewRequest(newBody = null, newScore = 0),
            ).andExpect(status().isBadRequest)

            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/${userReview.id}",
                accessToken = TestAccessTokens.user,
                body = EditReviewRequest(newBody = null, newScore = 6),
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should update review body and score`() {
            val editBody = EditReviewRequest(newBody = "Edited review", newScore = 5)

            fun getListing() = itemListingRepository.findById(sampleListing1.id!!).get()

            var listing1 = getListing()
            listing1.reviewCount shouldBe 2
            listing1.averageScore shouldBe BigDecimal("3.5")

            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/${userReview.id}",
                accessToken = TestAccessTokens.user,
                body = editBody,
            ).andExpect(status().isOk)

            val updatedReview = reviewRepository.findById(userReview.id!!).get()
            updatedReview.body shouldBe editBody.newBody
            updatedReview.score shouldBe editBody.newScore
            updatedReview.isEdited shouldBe true
            updatedReview.updatedAt shouldBeGreaterThan updatedReview.createdAt

            // Listing review data updated
            listing1 = getListing()
            listing1.reviewCount shouldBe 2
            listing1.averageScore shouldBe BigDecimal("4.0")
        }
    }

    @Nested
    inner class DeleteReviews {
        private lateinit var userReview: Review

        @BeforeEach
        fun addReviews() {
            initReviews()

            userReview = reviewRepository.findReviewsByUserId(TestUsers.user.id!!).last()
        }

        @Test
        fun `should return 403 forbidden on mismatch user ID`() {
            mockRequest(
                requestType = DELETE,
                path = "$BASE_PATH/${userReview.id}",
                accessToken = null,
            ).andExpect(status().isForbidden)

            mockRequest(
                requestType = DELETE,
                path = "$BASE_PATH/${userReview.id}",
                accessToken = TestAccessTokens.superadmin,
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should delete review and update listing`() {
            val prevListing = itemListingRepository.findById(sampleListing1.id!!).get()
            prevListing.reviewCount shouldBe 2
            prevListing.averageScore shouldBe BigDecimal("3.5")

            mockRequest(
                requestType = DELETE,
                path = "$BASE_PATH/${userReview.id}",
                accessToken = TestAccessTokens.user,
            ).andExpect(status().isNoContent)

            val listingReviews = reviewRepository.findReviewsByListingId(sampleListing1.id!!)
            listingReviews shouldHaveSize 1
            listingReviews.forNone { it.user.id shouldBe TestUsers.user.id }

            // Check for updated listing information
            val updatedListing = itemListingRepository.findById(sampleListing1.id!!).get()
            updatedListing.reviewCount shouldBe 1
            updatedListing.averageScore shouldBe BigDecimal("3.0")
        }
    }

    private fun initReviews() {
        // Add a review on each item listing
        val review1 = CreateReviewRequest(body = "Review 1", score = 4)

        mockRequest(
            requestType = POST,
            path = "$BASE_PATH/listing/${sampleListing1.id}",
            accessToken = TestAccessTokens.user,
            body = review1,
        )

        val review2 = CreateReviewRequest(body = "Review 2", score = 2)

        mockRequest(
            requestType = POST,
            path = "$BASE_PATH/listing/${sampleListing2.id}",
            accessToken = TestAccessTokens.user,
            body = review2,
        )

        val review3 = CreateReviewRequest(body = "Review 3", score = 3)

        mockRequest(
            requestType = POST,
            path = "$BASE_PATH/listing/${sampleListing1.id}",
            accessToken = TestAccessTokens.admin,
            body = review3,
        )
    }
}
