package com.mattrition.qmart.address

import com.mattrition.qmart.BaseH2Test
import com.mattrition.qmart.address.mapper.AddressMapper
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.jvm.optionals.getOrNull

class AddressControllerTest : BaseH2Test() {
    companion object {
        private const val BASE_PATH = "/api/address"
    }

    private fun generateAddress(isPrimary: Boolean = false) =
        Address(
            userId = TestUsers.user.id!!,
            isPrimary = isPrimary,
            firstName = "test",
            lastName = "test",
            city = "test",
            state = "test",
            zip = "12345",
            addressLine1 = "555 Quahog",
            phone = "555-555-5555",
        )

    private lateinit var sampleAddress1: Address
    private lateinit var sampleAddress2: Address

    @Autowired lateinit var addressRepository: AddressRepository

    @BeforeEach
    fun beforeEach() {
        sampleAddress1 = generateAddress(isPrimary = true)
        sampleAddress2 = generateAddress()
    }

    @Nested
    inner class GetAddress {
        @BeforeEach
        fun addAddress() {
            addressRepository.save(sampleAddress1)
            addressRepository.save(sampleAddress2)

            addressRepository.findAll().shouldHaveSize(2)
        }

        @Test
        fun `should return 403 forbidden on retrieving foreign addresses`() {
            mockRequest(
                requestType = HttpMethod.GET,
                path = "$BASE_PATH/user/${TestUsers.user.id}",
                accessToken = null,
            ).andExpect(status().isForbidden)

            mockRequest(
                requestType = HttpMethod.GET,
                path = "$BASE_PATH/user/${TestUsers.user.id!!}",
                accessToken = TestAccessTokens.superadmin,
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should retrieve user's primary address`() {
            mockRequest(
                requestType = HttpMethod.GET,
                path = "$BASE_PATH/primary/${TestUsers.user.id!!}",
                accessToken = TestAccessTokens.user,
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.isPrimary").value(true))
        }

        @Test
        fun `should retrieve all user's addresses`() {
            mockRequest(
                requestType = HttpMethod.GET,
                path = "$BASE_PATH/user/${TestUsers.user.id!!}",
                accessToken = TestAccessTokens.user,
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        }
    }

    @Nested
    inner class CreateAddress {
        @Test
        fun `should return 403 forbidden for non-users creating address`() {
            mockRequest(
                requestType = HttpMethod.POST,
                path = BASE_PATH,
                accessToken = null,
                body = AddressMapper.toDto(sampleAddress1),
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should create address and set as primary`() {
            sampleAddress2.isPrimary shouldBe false

            mockRequest(
                requestType = HttpMethod.POST,
                path = BASE_PATH,
                accessToken = TestAccessTokens.user,
                body = AddressMapper.toDto(sampleAddress2),
            ).andExpect(status().isCreated)

            val addresses = addressRepository.findByUserIdSorted(TestUsers.user.id!!)
            addresses shouldHaveSize 1

            val first = addresses.first()
            first.isPrimary shouldBe true
        }

        @Test
        fun `should return 400 bad request when creating another primary address`() {
            addressRepository.save(sampleAddress1)

            val userAddresses = addressRepository.findByUserIdSorted(TestUsers.user.id!!)
            userAddresses shouldHaveSize 1
            userAddresses.first().isPrimary shouldBe true

            mockRequest(
                requestType = HttpMethod.POST,
                path = BASE_PATH,
                accessToken = TestAccessTokens.user,
                body = AddressMapper.toDto(sampleAddress1),
            ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class DeleteAddress {
        private lateinit var savedAddress: Address

        @BeforeEach
        fun addAddress() {
            savedAddress = addressRepository.save(sampleAddress1)
        }

        @Test
        fun `should return 403 forbidden when deleting foreign address`() {
            mockRequest(
                requestType = HttpMethod.DELETE,
                path = "$BASE_PATH/${savedAddress.id!!}",
                accessToken = null,
            ).andExpect(status().isForbidden)

            mockRequest(
                requestType = HttpMethod.DELETE,
                path = "$BASE_PATH/${savedAddress.id!!}",
                accessToken = TestAccessTokens.superadmin,
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `should delete address and update primary`() {
            addressRepository.findById(savedAddress.id!!).getOrNull().shouldNotBeNull()

            val secondary = addressRepository.save(sampleAddress2)

            fun addresses() = addressRepository.findByUserIdSorted(TestUsers.user.id!!)
            addresses() shouldHaveSize 2

            mockRequest(
                requestType = HttpMethod.DELETE,
                path = "$BASE_PATH/${savedAddress.id!!}",
                accessToken = TestAccessTokens.user,
            ).andExpect(status().isOk)

            addressRepository.findById(savedAddress.id!!).getOrNull().shouldBeNull()

            val postAddresses = addresses()
            postAddresses shouldHaveSize 1

            val first = postAddresses.first()
            first.isPrimary shouldBe true
            first.id shouldBe secondary.id!!
        }
    }
}
