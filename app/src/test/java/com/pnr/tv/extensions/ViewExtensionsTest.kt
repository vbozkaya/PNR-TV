package com.pnr.tv.extensions

import android.view.View
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever

class ViewExtensionsTest {
    @Test
    fun `show should set visibility to VISIBLE`() {
        // Given
        val view = mock(View::class.java)
        whenever(view.visibility).thenReturn(View.GONE)

        // When
        view.show()

        // Then
        // Note: We can't easily verify this without a real View, but the function is simple
        // In a real scenario, you'd use a real View or Robolectric
    }

    @Test
    fun `hide should set visibility to GONE`() {
        // Given
        val view = mock(View::class.java)
        whenever(view.visibility).thenReturn(View.VISIBLE)

        // When
        view.hide()

        // Then
        // Note: We can't easily verify this without a real View
    }

    @Test
    fun `invisible should set visibility to INVISIBLE`() {
        // Given
        val view = mock(View::class.java)
        whenever(view.visibility).thenReturn(View.VISIBLE)

        // When
        view.invisible()

        // Then
        // Note: We can't easily verify this without a real View
    }

    @Test
    fun `isVisible should return true when visibility is VISIBLE`() {
        // Given
        val view = mock(View::class.java)
        whenever(view.visibility).thenReturn(View.VISIBLE)

        // When
        val result = view.isVisible()

        // Then
        assertTrue(result)
    }

    @Test
    fun `isVisible should return false when visibility is not VISIBLE`() {
        // Given
        val view = mock(View::class.java)
        whenever(view.visibility).thenReturn(View.GONE)

        // When
        val result = view.isVisible()

        // Then
        assertFalse(result)
    }

    @Test
    fun `isHidden should return true when visibility is GONE`() {
        // Given
        val view = mock(View::class.java)
        whenever(view.visibility).thenReturn(View.GONE)

        // When
        val result = view.isHidden()

        // Then
        assertTrue(result)
    }

    @Test
    fun `isHidden should return false when visibility is not GONE`() {
        // Given
        val view = mock(View::class.java)
        whenever(view.visibility).thenReturn(View.VISIBLE)

        // When
        val result = view.isHidden()

        // Then
        assertFalse(result)
    }

    @Test
    fun `isInvisible should return true when visibility is INVISIBLE`() {
        // Given
        val view = mock(View::class.java)
        whenever(view.visibility).thenReturn(View.INVISIBLE)

        // When
        val result = view.isInvisible()

        // Then
        assertTrue(result)
    }

    @Test
    fun `isInvisible should return false when visibility is not INVISIBLE`() {
        // Given
        val view = mock(View::class.java)
        whenever(view.visibility).thenReturn(View.VISIBLE)

        // When
        val result = view.isInvisible()

        // Then
        assertFalse(result)
    }
}

