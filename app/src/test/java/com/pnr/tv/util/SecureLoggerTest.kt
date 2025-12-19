package com.pnr.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SecureLogger için unit testler.
 * Hassas bilgilerin loglardan maskeleme işlemini test eder.
 */
class SecureLoggerTest {

    // ==================== sanitize() Tests - Pattern Masking ====================

    @Test
    fun `sanitize should mask password in message`() {
        // Given
        val message = "password: secret123"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("***"))
        assertFalse(result.contains("secret123"))
        assertTrue(result.contains("password"))
    }

    @Test
    fun `sanitize should mask API key in message`() {
        // Given
        val message = "api_key: abc123xyz789"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("abc***789"))
        assertFalse(result.contains("abc123xyz789"))
        assertTrue(result.contains("api_key"))
    }

    @Test
    fun `sanitize should mask DNS in message`() {
        // Given
        val message = "dns: 192.168.1.1"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("***"))
        assertFalse(result.contains("192.168.1.1"))
        assertTrue(result.contains("dns"))
    }

    @Test
    fun `sanitize should mask token in message`() {
        // Given
        val message = "token: abc123def456"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("abc***456"))
        assertFalse(result.contains("abc123def456"))
        assertTrue(result.contains("token"))
    }

    @Test
    fun `sanitize should mask secret in message`() {
        // Given
        val message = "secret: mySecretKey123"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("myS***123"))
        assertFalse(result.contains("mySecretKey123"))
        assertTrue(result.contains("secret"))
    }

    @Test
    fun `sanitize should mask username in message`() {
        // Given
        val message = "username: testuser123"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("tes***123"))
        assertFalse(result.contains("testuser123"))
        assertTrue(result.contains("username"))
    }

    @Test
    fun `sanitize should mask multiple sensitive values in message`() {
        // Given
        val message = "password: secret123, api_key: abc123xyz, dns: 192.168.1.1"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertFalse(result.contains("secret123"))
        assertFalse(result.contains("abc123xyz"))
        assertFalse(result.contains("192.168.1.1"))
        assertTrue(result.contains("***"))
    }

    @Test
    fun `sanitize should return original message when no sensitive data`() {
        // Given
        val message = "This is a normal log message without sensitive data"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertEquals(message, result)
    }

    @Test
    fun `sanitize should mask short values correctly`() {
        // Given - 3 karakter veya daha kısa değerler "***" olarak maskelenir
        val message = "password: ab"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("***"))
        assertFalse(result.contains("ab"))
    }

    @Test
    fun `sanitize should mask medium length values correctly`() {
        // Given - 4-6 karakter arası değerler: ilk ve son karakter gösterilir
        val message = "password: abcde"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("a***e"))
        assertFalse(result.contains("abcde"))
    }

    @Test
    fun `sanitize should mask long values correctly`() {
        // Given - 7+ karakter değerler: ilk 3 ve son 3 karakter gösterilir
        val message = "password: verylongpassword123"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("ver***123"))
        assertFalse(result.contains("verylongpassword123"))
    }

    @Test
    fun `sanitize should mask sensitive params in URL`() {
        // Given
        val message = "https://example.com/api?password=secret123&api_key=abc123xyz"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        // URL'deki parametreler maskelenir (sanitizeUrl metodu ile)
        // password=secret123 -> password=sec***123
        // api_key=abc123xyz -> api_key=abc***xyz
        assertFalse(result.contains("secret123"))
        assertFalse(result.contains("abc123xyz"))
        // En az bir değer maskelenmiş olmalı
        assertTrue(result.contains("***"))
    }

    @Test
    fun `sanitize should mask passwd format`() {
        // Given
        val message = "passwd: mypassword123"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("***"))
        assertFalse(result.contains("mypassword123"))
        assertTrue(result.contains("passwd"))
    }

    @Test
    fun `sanitize should mask pwd format`() {
        // Given
        val message = "pwd: secretpass"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("***"))
        assertFalse(result.contains("secretpass"))
        assertTrue(result.contains("pwd"))
    }

    @Test
    fun `sanitize should mask apikey format without underscore`() {
        // Given
        val message = "apikey: abc123xyz"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("abc***xyz"))
        assertFalse(result.contains("abc123xyz"))
        assertTrue(result.contains("apikey"))
    }

    @Test
    fun `sanitize should mask auth_token format`() {
        // Given
        val message = "auth_token: token123abc"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("tok***abc"))
        assertFalse(result.contains("token123abc"))
        assertTrue(result.contains("auth_token") || result.contains("token"))
    }

    @Test
    fun `sanitize should mask secret_key format`() {
        // Given
        val message = "secret_key: mysecret123"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("***"))
        assertFalse(result.contains("mysecret123"))
        assertTrue(result.contains("secret"))
    }

    @Test
    fun `sanitize should mask user format`() {
        // Given
        val message = "user: testuser123"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("tes***123"))
        assertFalse(result.contains("testuser123"))
        assertTrue(result.contains("user"))
    }

    @Test
    fun `sanitize should handle empty string`() {
        // Given
        val message = ""

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertEquals("", result)
    }

    @Test
    fun `sanitize should be case insensitive for password`() {
        // Given
        val message = "PASSWORD: secret123"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("***"))
        assertFalse(result.contains("secret123"))
    }

    @Test
    fun `sanitize should handle equals sign separator`() {
        // Given
        val message = "password=secret123"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("***"))
        assertFalse(result.contains("secret123"))
    }

    @Test
    fun `sanitize should mask server format`() {
        // Given
        val message = "server: 192.168.1.100"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertTrue(result.contains("***"))
        assertFalse(result.contains("192.168.1.100"))
        assertTrue(result.contains("server"))
    }

    @Test
    fun `sanitize should mask multiple sensitive params in URL`() {
        // Given
        val message = "https://api.example.com/login?username=testuser&password=secret123&token=abc123&api_key=xyz789"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertFalse(result.contains("testuser"))
        assertFalse(result.contains("secret123"))
        assertFalse(result.contains("abc123"))
        assertFalse(result.contains("xyz789"))
        assertTrue(result.contains("***"))
    }

    @Test
    fun `sanitize should handle comma separator in message`() {
        // Given
        val message = "password: secret123, api_key: abc123xyz"

        // When
        val result = SecureLogger.sanitize(message)

        // Then
        assertFalse(result.contains("secret123"))
        assertFalse(result.contains("abc123xyz"))
        assertTrue(result.contains("***"))
    }

    // ==================== Logging Methods Tests ====================

    @Test
    fun `d should sanitize and log message without tag`() {
        // Given
        val message = "password: secret123"

        // When & Then - Method should not throw exception
        // Timber static olduğu için mock'lanamaz, sadece exception fırlatmadan çalıştığını test ediyoruz
        SecureLogger.d(message)
    }

    @Test
    fun `d should sanitize and log message with tag`() {
        // Given
        val message = "api_key: abc123xyz"
        val tag = "TestTag"

        // When & Then - Method should not throw exception
        SecureLogger.d(message, tag)
    }

    @Test
    fun `i should sanitize and log message without tag`() {
        // Given
        val message = "token: token123"

        // When & Then - Method should not throw exception
        SecureLogger.i(message)
    }

    @Test
    fun `w should sanitize and log message without tag`() {
        // Given
        val message = "secret: mysecret123"

        // When & Then - Method should not throw exception
        SecureLogger.w(message)
    }

    @Test
    fun `e should sanitize and log message without tag and throwable`() {
        // Given
        val message = "password: secret123"

        // When & Then - Method should not throw exception
        SecureLogger.e(message)
    }

    @Test
    fun `e should sanitize and log message with throwable`() {
        // Given
        val message = "api_key: abc123xyz"
        val throwable = Exception("Test exception")

        // When & Then - Method should not throw exception
        SecureLogger.e(message, throwable)
    }
}

