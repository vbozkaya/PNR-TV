package com.pnr.tv.extensions

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * String extension functions için unit testler.
 */
class StringExtensionsTest {

    // ==================== normalizeDnsUrl() Testleri ====================

    @Test
    fun `normalizeDnsUrl should add http prefix and trailing slash when missing`() {
        // Given
        val input = "example.com"

        // When
        val result = input.normalizeDnsUrl()

        // Then
        assertEquals("http://example.com/", result)
    }

    @Test
    fun `normalizeDnsUrl should add trailing slash when http prefix exists`() {
        // Given
        val input = "http://example.com"

        // When
        val result = input.normalizeDnsUrl()

        // Then
        assertEquals("http://example.com/", result)
    }

    @Test
    fun `normalizeDnsUrl should preserve https and add trailing slash`() {
        // Given
        val input = "https://example.com"

        // When
        val result = input.normalizeDnsUrl()

        // Then
        assertEquals("https://example.com/", result)
    }

    @Test
    fun `normalizeDnsUrl should not modify already normalized URL`() {
        // Given
        val input = "http://example.com/"

        // When
        val result = input.normalizeDnsUrl()

        // Then
        assertEquals("http://example.com/", result)
    }

    @Test
    fun `normalizeDnsUrl should handle URL with trailing slash`() {
        // Given
        val input = "example.com/"

        // When
        val result = input.normalizeDnsUrl()

        // Then
        assertEquals("http://example.com/", result)
    }

    @Test
    fun `normalizeDnsUrl should handle empty string`() {
        // Given
        val input = ""

        // When
        val result = input.normalizeDnsUrl()

        // Then
        assertEquals("", result)
    }

    @Test
    fun `normalizeDnsUrl should trim whitespace`() {
        // Given
        val input = "  example.com  "

        // When
        val result = input.normalizeDnsUrl()

        // Then
        assertEquals("http://example.com/", result)
    }

    // ==================== normalizeBaseUrl() Testleri ====================

    @Test
    fun `normalizeBaseUrl should add http prefix when missing`() {
        // Given
        val input = "example.com"

        // When
        val result = input.normalizeBaseUrl()

        // Then
        assertEquals("http://example.com", result)
    }

    @Test
    fun `normalizeBaseUrl should remove trailing slash when present`() {
        // Given
        val input = "example.com/"

        // When
        val result = input.normalizeBaseUrl()

        // Then
        assertEquals("http://example.com", result)
    }

    @Test
    fun `normalizeBaseUrl should preserve http without trailing slash`() {
        // Given
        val input = "http://example.com"

        // When
        val result = input.normalizeBaseUrl()

        // Then
        assertEquals("http://example.com", result)
    }

    @Test
    fun `normalizeBaseUrl should remove trailing slash from http URL`() {
        // Given
        val input = "http://example.com/"

        // When
        val result = input.normalizeBaseUrl()

        // Then
        assertEquals("http://example.com", result)
    }

    @Test
    fun `normalizeBaseUrl should preserve https without trailing slash`() {
        // Given
        val input = "https://example.com"

        // When
        val result = input.normalizeBaseUrl()

        // Then
        assertEquals("https://example.com", result)
    }

    @Test
    fun `normalizeBaseUrl should remove trailing slash from https URL`() {
        // Given
        val input = "https://example.com/"

        // When
        val result = input.normalizeBaseUrl()

        // Then
        assertEquals("https://example.com", result)
    }

    @Test
    fun `normalizeBaseUrl should handle empty string`() {
        // Given
        val input = ""

        // When
        val result = input.normalizeBaseUrl()

        // Then
        assertEquals("", result)
    }

    @Test
    fun `normalizeBaseUrl should trim whitespace`() {
        // Given
        val input = "  example.com  "

        // When
        val result = input.normalizeBaseUrl()

        // Then
        assertEquals("http://example.com", result)
    }

    @Test
    fun `normalizeBaseUrl should handle URL with path and remove trailing slash`() {
        // Given
        val input = "http://example.com/api/"

        // When
        val result = input.normalizeBaseUrl()

        // Then
        assertEquals("http://example.com/api", result)
    }

    @Test
    fun `normalizeBaseUrl should handle URL with path without trailing slash`() {
        // Given
        val input = "http://example.com/api"

        // When
        val result = input.normalizeBaseUrl()

        // Then
        assertEquals("http://example.com/api", result)
    }
}

