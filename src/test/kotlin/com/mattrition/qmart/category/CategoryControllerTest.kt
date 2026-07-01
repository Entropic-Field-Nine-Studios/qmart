package com.mattrition.qmart.category

import com.mattrition.qmart.BaseH2Test
import com.mattrition.qmart.category.dto.CategoryDto
import com.mattrition.qmart.category.dto.CreateCategoryDto
import com.mattrition.qmart.category.dto.PatchCategoryDto
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.PATCH
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.module.kotlin.readValue

class CategoryControllerTest : BaseH2Test() {
    companion object {
        private const val BASE_PATH = "/api/categories"
    }

    @Nested
    inner class GetCategories {
        private lateinit var categoryElectric: Category
        private lateinit var categoryComp: Category
        private lateinit var categoryInactive: Category

        @BeforeEach
        fun init() {
            categoryElectric =
                categoryRepository.save(Category(name = "Electronics", slug = "electronics"))

            categoryComp =
                categoryRepository.save(Category(name = "Computer Parts", slug = "computer-parts"))

            categoryInactive =
                categoryRepository.save(
                    Category(name = "Grocery", slug = "grocery", isActive = false),
                )
        }

        @Test
        fun `should get only active categories`() {
            val result =
                mockRequest(requestType = GET, path = BASE_PATH, accessToken = null)
                    .andExpect(status().isOk)
                    .andReturn()

            val body = result.response.contentAsString
            val categories = objectMapper.readValue<List<CategoryDto>>(body)

            categories.forAll { it.isActive shouldBe true }
        }

        @Test
        fun `should return 403 forbidden when retrieving every category as non-admin`() {
            mockRequest(requestType = GET, path = "$BASE_PATH/all", accessToken = null)
                .andExpect(status().isUnauthorized)

            mockRequest(
                requestType = GET,
                path = "$BASE_PATH/all",
                accessToken = TestAccessTokens.user,
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return ALL categories`() {
            val result =
                mockRequest(
                    requestType = GET,
                    path = "$BASE_PATH/all",
                    accessToken = TestAccessTokens.admin,
                ).andExpect(status().isOk)
                    .andReturn()

            val body = result.response.contentAsString
            val categories = objectMapper.readValue<List<CategoryDto>>(body)

            categories.filter { it.isActive }.shouldNotBeEmpty()
            categories.filterNot { it.isActive }.shouldNotBeEmpty()
        }
    }

    @Nested
    inner class CreateCategory {
        @Test
        fun `should return 403 forbidden when accessing as non-admin`() {
            val req = CreateCategoryDto(name = "Sample Category", slug = "category")

            mockRequest(requestType = POST, path = BASE_PATH, accessToken = null, body = req)
                .andExpect(status().isUnauthorized)

            mockRequest(
                requestType = POST,
                path = BASE_PATH,
                accessToken = TestAccessTokens.user,
                body = req,
            ).andExpect(status().isForbidden)

            mockRequest(
                requestType = POST,
                path = BASE_PATH,
                accessToken = TestAccessTokens.moderator,
                body = req,
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should create category`() {
            val req = CreateCategoryDto(name = "Sample Category", slug = "category")

            mockRequest(
                requestType = POST,
                path = BASE_PATH,
                accessToken = TestAccessTokens.admin,
                body = req,
            ).andExpect(status().isCreated)

            categoryRepository.findCategoryBySlug(req.slug).shouldNotBeNull()
        }

        @Test
        fun `should return 400 bad request on invalid slug`() {
            val invalidSlugs =
                listOf(
                    "hello world",
                    "two words",
                    "hello_world",
                    "test_slug",
                    "hello!",
                    "slug$",
                    "weird@",
                    "hash#tag",
                    " hello",
                    "hello ",
                    " hello ",
                    "",
                    " ",
                    "   ",
                    "café",
                    "naïve",
                    "emoji-🔥",
                    "a*b",
                    "a+b",
                    "a=b",
                    "a/b",
                    "smart–dash",
                    "smart—dash",
                    "curly“quotes”",
                )

            invalidSlugs.forEach { invalidStr ->
                val req = CreateCategoryDto(name = "Sample Category", slug = invalidStr)

                mockRequest(
                    requestType = POST,
                    path = BASE_PATH,
                    accessToken = TestAccessTokens.admin,
                    body = req,
                ).andExpect { result ->
                    if (result.response.status != HttpStatus.BAD_REQUEST.value()) {
                        fail(
                            "Expected status 400 for slug: '$invalidStr' but got ${result.response.status}",
                        )
                    }
                }
            }
        }

        @Test
        fun `should return 403 forbidden when creating a category with a duplicate slug`() {
            // Save existing category
            categoryRepository.save(Category(name = "Test", slug = "test"))

            val req = CreateCategoryDto(name = "Test 2", slug = "TEST")

            mockRequest(
                requestType = POST,
                path = BASE_PATH,
                accessToken = TestAccessTokens.admin,
                body = req,
            ).andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class PatchCategory {
        private lateinit var sampleCategory: Category

        @BeforeEach
        fun init() {
            sampleCategory =
                categoryRepository.save(
                    Category(name = "Sample Category", slug = "sample-category"),
                )
        }

        @Test
        fun `should patch category`() {
            val req =
                PatchCategoryDto(name = "Best Category", slug = "super-category", isActive = true)

            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/${sampleCategory.id!!}",
                accessToken = TestAccessTokens.admin,
                body = req,
            ).andExpect(status().isOk)

            val category = categoryRepository.findById(sampleCategory.id!!).get()
            category.slug shouldBe req.slug
            category.isActive shouldBe true
            category.name shouldBe req.name
            category.updatedAt shouldBeGreaterThan category.createdAt
        }

        @Test
        fun `should return 403 forbidden for non-admins`() {
            val req =
                PatchCategoryDto(name = "Best Category", slug = "super-category", isActive = true)

            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/${sampleCategory.id!!}",
                accessToken = null,
                body = req,
            ).andExpect(status().isUnauthorized)

            mockRequest(
                requestType = PATCH,
                path = "$BASE_PATH/${sampleCategory.id!!}",
                accessToken = TestAccessTokens.user,
                body = req,
            ).andExpect(status().isForbidden)
        }
    }
}
