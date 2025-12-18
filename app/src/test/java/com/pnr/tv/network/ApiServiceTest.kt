package com.pnr.tv.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * ApiService için integration testleri.
 * MockWebServer kullanarak HTTP isteklerini simüle eder.
 *
 * Note: Bu testler network layer'ın doğru çalıştığını doğrular.
 * Gerçek API çağrıları yerine mock server kullanır.
 */
class ApiServiceTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val moshi =
            Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

        val retrofit =
            Retrofit.Builder()
                .baseUrl(mockWebServer.url("/"))
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

        apiService = retrofit.create(ApiService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getMovies should parse response correctly`() =
        runBlocking {
            // Given
            val mockResponse =
                """
                [
                    {
                        "stream_id": 1,
                        "name": "Test Movie",
                        "rating": "8.5",
                        "plot": "Test plot",
                        "category_id": "1",
                        "added": "2024-01-01",
                        "container_extension": "mp4",
                        "tmdb_id": 100
                    }
                ]
                """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(mockResponse),
            )

            // When
            val result = apiService.getMovies("testuser", "testpass", "get_vod_streams")

            // Then
            assertTrue(result.isNotEmpty())
            assertEquals(1, result.size)
            assertEquals("Test Movie", result[0].name)
            assertEquals("8.5", result[0].rating)
            assertEquals("Test plot", result[0].plot)
            assertEquals("1", result[0].categoryId)
            assertEquals("100", result[0].tmdb)
        }

    @Test
    fun `getLiveStreams should parse response correctly`() =
        runBlocking {
            // Given
            val mockResponse =
                """
                [
                    {
                        "stream_id": 1,
                        "name": "Test Channel",
                        "stream_icon": "http://example.com/icon.png",
                        "category_id": 1
                    }
                ]
                """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(mockResponse),
            )

            // When
            val result = apiService.getLiveStreams("testuser", "testpass", "get_live_streams")

            // Then
            assertTrue(result.isNotEmpty())
            assertEquals(1, result.size)
            assertEquals("Test Channel", result[0].name)
            assertEquals(1, result[0].streamId)
            assertEquals(1, result[0].categoryId)
        }

    @Test
    fun `getUserInfo should parse response correctly`() =
        runBlocking {
            // Given
            val mockResponse =
                """
                {
                    "username": "testuser",
                    "password": "testpass",
                    "status": "Active",
                    "exp_date": "2024-12-31"
                }
                """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(mockResponse),
            )

            // When
            val result = apiService.getUserInfo("testuser", "testpass")

            // Then
            assertNotNull(result)
            val userInfo = result.extractUserInfo()
            assertNotNull(userInfo)
            assertEquals("testuser", userInfo?.username)
            assertEquals("testpass", userInfo?.password)
            assertEquals("Active", userInfo?.status)
        }

    @Test
    fun `getMovies should handle empty response`() =
        runBlocking {
            // Given
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("[]"),
            )

            // When
            val result = apiService.getMovies("testuser", "testpass", "get_vod_streams")

            // Then
            assertTrue(result.isEmpty())
        }

    @Test
    fun `getLiveStreams should handle empty response`() =
        runBlocking {
            // Given
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("[]"),
            )

            // When
            val result = apiService.getLiveStreams("testuser", "testpass", "get_live_streams")

            // Then
            assertTrue(result.isEmpty())
        }

    // Note: Error handling tests (4xx, 5xx responses) should be tested
    // at the repository level where safeApiCall handles them.
    // These tests focus on successful response parsing.
}
