package com.pnr.tv.domain

import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.db.entity.UserAccountEntity
import com.pnr.tv.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/**
 * BuildLiveStreamUrlUseCase için unit testler.
 * Stream URL oluşturma mantığının doğru çalıştığını doğrular.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuildLiveStreamUrlUseCaseTest {

    private lateinit var useCase: BuildLiveStreamUrlUseCase
    private lateinit var mockUserRepository: UserRepository

    @Before
    fun setup() {
        mockUserRepository = mock()
        useCase = BuildLiveStreamUrlUseCase(mockUserRepository)
    }

    @Test
    fun `invoke should return correct URL when user exists`() = runTest {
        // Given
        val user = UserAccountEntity(
            id = 1,
            accountName = "Test Account",
            username = "testuser",
            password = "testpass",
            dns = "https://example.com"
        )
        val channel = LiveStreamEntity(
            streamId = 12345,
            name = "Test Channel",
            streamIconUrl = null,
            categoryId = 1,
            categoryName = null
        )
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(user))

        // When
        val result = useCase(channel)

        // Then
        assertEquals("https://example.com/live/testuser/testpass/12345.ts", result)
    }

    @Test
    fun `invoke should normalize DNS URL correctly`() = runTest {
        // Given - DNS without protocol
        val user = UserAccountEntity(
            id = 1,
            accountName = "Test Account",
            username = "user",
            password = "pass",
            dns = "example.com"
        )
        val channel = LiveStreamEntity(
            streamId = 123,
            name = "Channel",
            streamIconUrl = null,
            categoryId = 1,
            categoryName = null
        )
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(user))

        // When
        val result = useCase(channel)

        // Then - Should add http:// prefix
        assertEquals("http://example.com/live/user/pass/123.ts", result)
    }

    @Test
    fun `invoke should handle DNS with trailing slash`() = runTest {
        // Given
        val user = UserAccountEntity(
            id = 1,
            accountName = "Test",
            username = "user",
            password = "pass",
            dns = "http://example.com/"
        )
        val channel = LiveStreamEntity(
            streamId = 456,
            name = "Channel",
            streamIconUrl = null,
            categoryId = 1,
            categoryName = null
        )
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(user))

        // When
        val result = useCase(channel)

        // Then - Should remove trailing slash from base URL
        assertEquals("http://example.com/live/user/pass/456.ts", result)
    }

    @Test
    fun `invoke should handle HTTPS DNS`() = runTest {
        // Given
        val user = UserAccountEntity(
            id = 1,
            accountName = "Test",
            username = "user",
            password = "pass",
            dns = "https://secure.example.com"
        )
        val channel = LiveStreamEntity(
            streamId = 789,
            name = "Channel",
            streamIconUrl = null,
            categoryId = 1,
            categoryName = null
        )
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(user))

        // When
        val result = useCase(channel)

        // Then
        assertEquals("https://secure.example.com/live/user/pass/789.ts", result)
    }

    @Test
    fun `invoke should return null when user is null`() = runTest {
        // Given
        val channel = LiveStreamEntity(
            streamId = 123,
            name = "Channel",
            streamIconUrl = null,
            categoryId = 1,
            categoryName = null
        )
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(null))

        // When
        val result = useCase(channel)

        // Then
        assertNull(result)
    }

    @Test
    fun `invoke should handle special characters in username and password`() = runTest {
        // Given
        val user = UserAccountEntity(
            id = 1,
            accountName = "Test",
            username = "user@domain",
            password = "pass#123",
            dns = "https://example.com"
        )
        val channel = LiveStreamEntity(
            streamId = 123,
            name = "Channel",
            streamIconUrl = null,
            categoryId = 1,
            categoryName = null
        )
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(user))

        // When
        val result = useCase(channel)

        // Then - Special characters should be included in URL
        assertEquals("https://example.com/live/user@domain/pass#123/123.ts", result)
    }

    @Test
    fun `invoke should handle empty stream ID`() = runTest {
        // Given
        val user = UserAccountEntity(
            id = 1,
            accountName = "Test",
            username = "user",
            password = "pass",
            dns = "https://example.com"
        )
        val channel = LiveStreamEntity(
            streamId = 0,
            name = "Channel",
            streamIconUrl = null,
            categoryId = 1,
            categoryName = null
        )
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(user))

        // When
        val result = useCase(channel)

        // Then - Should still create URL with stream ID 0
        assertEquals("https://example.com/live/user/pass/0.ts", result)
    }

    @Test
    fun `invoke should handle long stream ID`() = runTest {
        // Given
        val user = UserAccountEntity(
            id = 1,
            accountName = "Test",
            username = "user",
            password = "pass",
            dns = "https://example.com"
        )
        val channel = LiveStreamEntity(
            streamId = 999999,
            name = "Channel",
            streamIconUrl = null,
            categoryId = 1,
            categoryName = null
        )
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(user))

        // When
        val result = useCase(channel)

        // Then
        assertEquals("https://example.com/live/user/pass/999999.ts", result)
    }

    @Test
    fun `invoke should handle IP address as DNS`() = runTest {
        // Given
        val user = UserAccountEntity(
            id = 1,
            accountName = "Test",
            username = "user",
            password = "pass",
            dns = "192.168.1.100"
        )
        val channel = LiveStreamEntity(
            streamId = 123,
            name = "Channel",
            streamIconUrl = null,
            categoryId = 1,
            categoryName = null
        )
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(user))

        // When
        val result = useCase(channel)

        // Then - Should add http:// prefix to IP
        assertEquals("http://192.168.1.100/live/user/pass/123.ts", result)
    }

    @Test
    fun `invoke should handle DNS with port number`() = runTest {
        // Given
        val user = UserAccountEntity(
            id = 1,
            accountName = "Test",
            username = "user",
            password = "pass",
            dns = "http://example.com:8080"
        )
        val channel = LiveStreamEntity(
            streamId = 123,
            name = "Channel",
            streamIconUrl = null,
            categoryId = 1,
            categoryName = null
        )
        whenever(mockUserRepository.currentUser).thenReturn(flowOf(user))

        // When
        val result = useCase(channel)

        // Then
        assertEquals("http://example.com:8080/live/user/pass/123.ts", result)
    }
}

