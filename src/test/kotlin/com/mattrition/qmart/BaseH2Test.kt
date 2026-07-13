package com.mattrition.qmart

import com.mattrition.qmart.auth.CookieService
import com.mattrition.qmart.auth.CustomUserDetails
import com.mattrition.qmart.auth.JwtService
import com.mattrition.qmart.category.Category
import com.mattrition.qmart.category.CategoryRepository
import com.mattrition.qmart.config.SecurityConfig
import com.mattrition.qmart.itemlisting.ItemListing
import com.mattrition.qmart.itemlisting.ItemListingRepository
import com.mattrition.qmart.user.User
import com.mattrition.qmart.user.UserRepository
import com.mattrition.qmart.user.UserRole
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.minutes

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@Import(SecurityConfig::class)
abstract class BaseH2Test {
    @Autowired protected lateinit var objectMapper: ObjectMapper

    @Autowired protected lateinit var passwordEncoder: PasswordEncoder

    @Autowired protected lateinit var userRepository: UserRepository

    @Autowired protected lateinit var itemListingRepository: ItemListingRepository

    @Autowired protected lateinit var categoryRepository: CategoryRepository

    @Autowired protected lateinit var jwtService: JwtService

    @Autowired protected lateinit var mockMvc: MockMvc

    /**
     * A container for referencing preset registered users.
     *
     * @property user Default user.
     * @property moderator
     * @property admin
     * @property superadmin
     */
    protected object TestUsers {
        lateinit var user: User
        lateinit var moderator: User
        lateinit var admin: User
        lateinit var superadmin: User
    }

    /**
     * A container for holding preset Java Web Tokens.
     *
     * @property user Represents a regular user token.
     * @property moderator Client with moderator-level privilege.
     * @property admin Client with admin-level privilege.
     * @property superadmin Client with superadmin-level privilege.
     */
    protected object TestAccessTokens {
        lateinit var user: String
        lateinit var moderator: String
        lateinit var admin: String
        lateinit var superadmin: String

        fun toMap(): Map<String, String> =
            mapOf(
                UserRole.USER to user,
                UserRole.MODERATOR to moderator,
                UserRole.ADMIN to admin,
                UserRole.SUPERADMIN to superadmin,
            )
    }

    protected object TestRefreshTokens {
        lateinit var user: String
        lateinit var moderator: String
        lateinit var admin: String
        lateinit var superadmin: String
    }

    @BeforeAll
    fun seedUsers() {
        val json = javaClass.getResourceAsStream("/data/users.json")
        val users = objectMapper.readValue(json, Array<TestUserSeed>::class.java)

        userRepository.deleteAll()

        users.forEach { seed ->
            val newUser =
                userRepository.save(
                    User(
                        username = seed.username,
                        passwordHash =
                            passwordEncoder.encode(seed.password)
                                ?: throw RuntimeException(
                                    "Cannot encode password: ${seed.password}",
                                ),
                        email = seed.email,
                        role = seed.role,
                    ),
                )

            val accessToken =
                jwtService.generateToken(
                    username = seed.username,
                    id = newUser.id!!,
                    role = newUser.role,
                    expirationMillis = 5.minutes.inWholeMilliseconds,
                )

            val refreshToken =
                jwtService.generateToken(
                    username = seed.username,
                    id = newUser.id!!,
                    role = newUser.role,
                    expirationMillis = 10.minutes.inWholeMilliseconds,
                )

            when (seed.role.uppercase()) {
                UserRole.SUPERADMIN -> {
                    TestUsers.superadmin = newUser
                    TestAccessTokens.superadmin = accessToken
                    TestRefreshTokens.superadmin = refreshToken
                }

                UserRole.ADMIN -> {
                    TestUsers.admin = newUser
                    TestAccessTokens.admin = accessToken
                    TestRefreshTokens.admin = refreshToken
                }

                UserRole.MODERATOR -> {
                    TestUsers.moderator = newUser
                    TestAccessTokens.moderator = accessToken
                    TestRefreshTokens.moderator = refreshToken
                }

                else -> {
                    TestUsers.user = newUser
                    TestAccessTokens.user = accessToken
                    TestRefreshTokens.user = refreshToken
                }
            }
        }
    }

    /**
     * This method first creates a new category, then initializes the item listing repository with
     * two listings:
     * 1. Sold by `moderator` with a price of 100
     * 2. Sold by `admin` with a price of 250
     *
     * @return All item listings.
     */
    protected fun initListings(): List<ItemListing> {
        val category =
            categoryRepository.save(Category(name = "Sample Category", slug = "sample-category"))

        itemListingRepository.save(
            ItemListing(
                sellerId = TestUsers.moderator.id!!,
                title = "Test Listing 1",
                description = "Test listing.",
                price = BigDecimal.valueOf(100),
                categoryId = category.id!!,
            ),
        )

        itemListingRepository.save(
            ItemListing(
                sellerId = TestUsers.admin.id!!,
                title = "Test Listing 2",
                description = "Test listing, but admin.",
                price = BigDecimal.valueOf(250),
                categoryId = category.id!!,
            ),
        )

        return itemListingRepository.findAll()
    }

    protected fun ItemListing.category() = categoryRepository.findById(this.categoryId).getOrNull()

    /**
     * Sends a mock HTTP request to a specified rest controller.
     *
     * @param requestType Method type of the controller.
     * @param path URI of the controller.
     * @param accessToken Which [TestAccessTokens] to use for this call, or `null` if non-user.
     * @param refreshToken Which [TestAccessTokens] to use for the refresh token, or `null` if
     *   non-user.
     * @param body Data body in the request for `POST` calls.
     * @param params Parameters to add to the request.
     */
    protected fun mockRequest(
        requestType: HttpMethod,
        path: String,
        accessToken: String?,
        refreshToken: String? = null,
        body: Any? = null,
        params: Map<String, String> = emptyMap(),
    ): ResultActions {
        val builder =
            when (requestType) {
                HttpMethod.GET -> MockMvcRequestBuilders.get(path)
                HttpMethod.POST -> MockMvcRequestBuilders.post(path)
                HttpMethod.PUT -> MockMvcRequestBuilders.put(path)
                HttpMethod.DELETE -> MockMvcRequestBuilders.delete(path)
                HttpMethod.OPTIONS -> MockMvcRequestBuilders.options(path)
                HttpMethod.HEAD -> MockMvcRequestBuilders.head(path)
                HttpMethod.PATCH -> MockMvcRequestBuilders.patch(path)
                else -> throw RuntimeException("Unhandled request: $requestType")
            }

        if (body != null) {
            builder
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        }

        if (accessToken != null) {
            builder.cookie(Cookie(CookieService.ACCESS_TOKEN_NAME, accessToken))
        }

        if (refreshToken != null) {
            builder.cookie(Cookie(CookieService.REFRESH_TOKEN_NAME, refreshToken))
        }

        params.forEach { (k, v) -> builder.param(k, v) }

        return mockMvc.perform(builder)
    }

    /**
     * Runs mock request tests on an endpoint which verify correct permissions.
     *
     * @param requestType Method type of the endpoint.
     * @param path URI of the endpoint.
     * @param minRole Minimum level of [UserRole] permission required for access, or null if no
     *   permission is required.
     * @param body Data to attach on the request.
     * @param params Any request parameters.
     */
    protected fun testPermissions(
        requestType: HttpMethod,
        path: String,
        minRole: String? = null,
        body: Any? = null,
        params: Map<String, String> = emptyMap(),
    ) {
        // Test for non-authorization
        val guestMatcher =
            if (minRole != null) status().isUnauthorized else status().is2xxSuccessful

        mockRequest(requestType, path, accessToken = null, body = body, params = params)
            .andExpect(guestMatcher)

        TestAccessTokens.toMap().forEach { (role, accessToken) ->
            // Expect successful response if iterated role is equal to or
            // better than the minimum required
            val expectedStatusMatcher =
                if (UserRole.compare(role, minRole ?: "") >= 0) {
                    status().is2xxSuccessful
                } else {
                    status().isForbidden
                }

            mockRequest(requestType, path, accessToken = accessToken, body = body, params = params)
                .andExpect(expectedStatusMatcher)
        }
    }

    /**
     * Inserts authentication information into the application. Useful for getting past certain
     * service rules.
     *
     * @param user User to authenticate with.
     */
    protected fun authenticate(user: User) {
        val principal = CustomUserDetails(user)
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)

        SecurityContextHolder.getContext().authentication = auth
    }

    /** Removes authentication information to simulate a non-user using the app. */
    protected fun clearAuth() {
        SecurityContextHolder.clearContext()
    }
}
