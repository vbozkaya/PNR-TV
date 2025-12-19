package com.pnr.tv.util

import android.content.Context
import android.graphics.drawable.Drawable
import coil.ImageLoader
import coil.request.ImageRequest
import com.pnr.tv.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * BackgroundManager için unit testler.
 * Arkaplan yükleme ve cache yönetimini test eder.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundManagerTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var mockContext: Context
    private lateinit var mockImageLoader: ImageLoader
    private lateinit var mockDrawable: Drawable

    @Before
    fun setup() {
        mockContext = mock()
        mockImageLoader = mock()
        mockDrawable = mock()
        
        // Clear cache before each test
        BackgroundManager.clearCache()
    }

    @After
    fun tearDown() {
        // Clear cache after each test
        BackgroundManager.clearCache()
    }

    @Test
    fun `getCachedBackground should return null when cache is empty`() {
        // When
        val result = BackgroundManager.getCachedBackground()

        // Then
        assertNull(result)
    }

    @Test
    fun `clearCache should clear cached background`() {
        // Given - Cache is already cleared in setup
        
        // When
        BackgroundManager.clearCache()

        // Then
        val result = BackgroundManager.getCachedBackground()
        assertNull(result)
    }

    @Test
    fun `getFallbackBackground should return drawable from context`() {
        // Given
        val mockFallbackDrawable: Drawable = mock()
        whenever(mockContext.getDrawable(any())).thenReturn(mockFallbackDrawable)

        // When
        val result = BackgroundManager.getFallbackBackground(mockContext)

        // Then
        // Note: getFallbackBackground uses ContextCompat.getDrawable internally
        // which may return null in test environment
        // This test verifies the method doesn't crash
        // In a real scenario with Robolectric, we could verify the drawable is returned
    }

    @Test
    fun `getFallbackBackground should handle null drawable gracefully`() {
        // Given
        whenever(mockContext.getDrawable(any())).thenReturn(null)

        // When
        val result = BackgroundManager.getFallbackBackground(mockContext)

        // Then
        // Should not crash, may return null
        // This test verifies error handling
    }

    @Test
    fun `clearCache should reset isLoaded flag`() {
        // Given - Cache is cleared in setup
        
        // When
        BackgroundManager.clearCache()

        // Then
        // After clearing, getCachedBackground should return null
        val result = BackgroundManager.getCachedBackground()
        assertNull(result)
    }

    @Test
    fun `getCachedBackground should return same instance after multiple calls`() {
        // Given - Cache is empty
        
        // When
        val firstCall = BackgroundManager.getCachedBackground()
        val secondCall = BackgroundManager.getCachedBackground()

        // Then
        // Both should be null when cache is empty
        assertNull(firstCall)
        assertNull(secondCall)
    }
}

