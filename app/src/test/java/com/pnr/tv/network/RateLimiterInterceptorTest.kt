package com.pnr.tv.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

/**
 * RateLimiterInterceptor için unit testler.
 * Rate limiting mantığının doğru çalıştığını doğrular.
 */
class RateLimiterInterceptorTest {
    private lateinit var interceptor: RateLimiterInterceptor
    private lateinit var mockChain: Interceptor.Chain
    private lateinit var mockRequest: Request
    private lateinit var mockResponse: Response

    @Before
    fun setup() {
        mockRequest =
            Request.Builder()
                .url("https://example.com/api")
                .build()

        mockResponse =
            Response.Builder()
                .request(mockRequest)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()

        mockChain = mock()
        whenever(mockChain.request()).thenReturn(mockRequest)
        whenever(mockChain.proceed(any())).thenReturn(mockResponse)
    }

    @Test
    fun `intercept should proceed immediately on first request`() {
        // Given
        interceptor = RateLimiterInterceptor(minDelayMs = 100L)

        // When
        val result = interceptor.intercept(mockChain)

        // Then
        verify(mockChain).proceed(mockRequest)
        assertTrue(result == mockResponse)
    }

    @Test
    fun `intercept should wait when requests are too fast`() {
        // Given
        val testInterceptor = RateLimiterInterceptor(minDelayMs = 100L)
        val testChain = mock<Interceptor.Chain>()
        whenever(testChain.request()).thenReturn(mockRequest)
        whenever(testChain.proceed(any())).thenReturn(mockResponse)

        // First request - should proceed immediately
        testInterceptor.intercept(testChain)

        // When - Second request immediately after
        val startTime = System.currentTimeMillis()
        testInterceptor.intercept(testChain)
        val elapsedTime = System.currentTimeMillis() - startTime

        // Then - Should have waited at least minDelayMs
        assertTrue(elapsedTime >= 90L) // Allow 10ms tolerance
        verify(testChain, org.mockito.kotlin.times(2)).proceed(mockRequest)
    }

    @Test
    fun `intercept should not wait when enough time has passed`() {
        // Given
        val testInterceptor = RateLimiterInterceptor(minDelayMs = 50L)
        val testChain = mock<Interceptor.Chain>()
        whenever(testChain.request()).thenReturn(mockRequest)
        whenever(testChain.proceed(any())).thenReturn(mockResponse)

        // First request
        testInterceptor.intercept(testChain)

        // Wait more than minDelayMs
        Thread.sleep(60L)

        // When - Second request after delay
        val startTime = System.currentTimeMillis()
        testInterceptor.intercept(testChain)
        val elapsedTime = System.currentTimeMillis() - startTime

        // Then - Should proceed immediately (no waiting)
        assertTrue(elapsedTime < 30L) // Should be very fast
        verify(testChain, org.mockito.kotlin.times(2)).proceed(mockRequest)
    }

    @Test
    fun `intercept should handle multiple sequential requests correctly`() {
        // Given
        val testInterceptor = RateLimiterInterceptor(minDelayMs = 50L)
        val testChain = mock<Interceptor.Chain>()
        whenever(testChain.request()).thenReturn(mockRequest)
        whenever(testChain.proceed(any())).thenReturn(mockResponse)

        // When - Make 3 requests
        val startTime = System.currentTimeMillis()
        testInterceptor.intercept(testChain)
        testInterceptor.intercept(testChain)
        testInterceptor.intercept(testChain)
        val totalTime = System.currentTimeMillis() - startTime

        // Then - Should have waited between requests
        // First request: 0ms wait
        // Second request: ~50ms wait
        // Third request: ~50ms wait
        // Total: ~100ms minimum
        assertTrue(totalTime >= 90L) // Allow tolerance
        verify(testChain, org.mockito.kotlin.times(3)).proceed(mockRequest)
    }

    @Test
    fun `intercept should update lastRequestTime after each request`() {
        // Given
        interceptor = RateLimiterInterceptor(minDelayMs = 100L)

        // When
        interceptor.intercept(mockChain)
        Thread.sleep(150L) // Wait more than minDelayMs
        interceptor.intercept(mockChain)

        // Then - Second request should proceed immediately (no wait)
        val startTime = System.currentTimeMillis()
        interceptor.intercept(mockChain)
        val elapsedTime = System.currentTimeMillis() - startTime

        // Should wait because last request was just made
        assertTrue(elapsedTime >= 90L)
    }

    @Test
    fun `intercept should handle different minDelayMs values`() {
        // Given - Very short delay
        interceptor = RateLimiterInterceptor(minDelayMs = 10L)

        // When
        interceptor.intercept(mockChain)
        val startTime = System.currentTimeMillis()
        interceptor.intercept(mockChain)
        val elapsedTime = System.currentTimeMillis() - startTime

        // Then - Should wait approximately 10ms
        assertTrue(elapsedTime >= 5L) // Allow tolerance for very short delays
        assertTrue(elapsedTime < 30L) // Should not wait too long
    }

    @Test
    fun `intercept should propagate response from chain`() {
        // Given
        interceptor = RateLimiterInterceptor(minDelayMs = 0L)

        // When
        val result = interceptor.intercept(mockChain)

        // Then
        assertEquals(mockResponse, result)
        verify(mockChain).proceed(mockRequest)
    }

    @Test
    fun `intercept should handle chain exceptions`() {
        // Given
        interceptor = RateLimiterInterceptor(minDelayMs = 0L)
        val exception = IOException("Network error")
        whenever(mockChain.proceed(any())).thenThrow(exception)

        // When/Then
        try {
            interceptor.intercept(mockChain)
            assertTrue("Should have thrown exception", false)
        } catch (e: IOException) {
            assertEquals(exception, e)
        }
    }

    @Test
    fun `intercept should work with zero delay`() {
        // Given
        interceptor = RateLimiterInterceptor(minDelayMs = 0L)

        // When - Multiple requests with zero delay
        val startTime = System.currentTimeMillis()
        interceptor.intercept(mockChain)
        interceptor.intercept(mockChain)
        interceptor.intercept(mockChain)
        val elapsedTime = System.currentTimeMillis() - startTime

        // Then - Should proceed immediately (very fast)
        assertTrue(elapsedTime < 50L) // Should be very fast
    }

    @Test
    fun `intercept should handle thread interruption gracefully`() {
        // Given
        interceptor = RateLimiterInterceptor(minDelayMs = 1000L) // Long delay
        interceptor.intercept(mockChain) // First request

        // When - Interrupt thread during wait
        val thread =
            Thread {
                try {
                    interceptor.intercept(mockChain)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        thread.start()
        Thread.sleep(10L) // Let it start waiting
        thread.interrupt()
        thread.join(100L)

        // Then - Thread should handle interruption
        assertTrue(!thread.isAlive || thread.isInterrupted)
    }
}
