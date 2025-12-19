package com.pnr.tv.util

import android.content.Context
import com.pnr.tv.R
import com.pnr.tv.repository.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import java.io.IOException

/**
 * ErrorHelper için unit testler.
 * Hata yönetimi mantığının doğru çalıştığını doğrular.
 */
class ErrorHelperTest {
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mockContext = mock()
        // Setup default string resources
        whenever(mockContext.getString(R.string.error_network_generic, 404, ""))
            .thenReturn("Ağ hatası: 404 ")
        whenever(mockContext.getString(R.string.error_network_connection))
            .thenReturn("Ağ bağlantı hatası")
        whenever(mockContext.getString(R.string.error_unexpected, "Test error"))
            .thenReturn("Beklenmeyen hata: Test error")
        whenever(mockContext.getString(R.string.error_unknown))
            .thenReturn("Bilinmeyen hata")
        whenever(mockContext.getString(R.string.error_user_not_found))
            .thenReturn("Kullanıcı bilgisi bulunamadı veya DNS bilgisi eksik")
        whenever(mockContext.getString(R.string.error_image_preload, "Test error"))
            .thenReturn("Resim ön yükleme hatası: Test error")
        whenever(mockContext.getString(R.string.error_timeout_read))
            .thenReturn("Ağ bağlantı hatası")
        whenever(mockContext.getString(R.string.error_timeout_connect))
            .thenReturn("Ağ bağlantı hatası")
        whenever(mockContext.getString(R.string.error_timeout))
            .thenReturn("Ağ bağlantı hatası")
    }

    @Test
    fun `createError should return HttpError for HttpException`() {
        // Given - Mock HttpException directly
        val httpException = mock<HttpException>()
        whenever(httpException.code()).thenReturn(404)
        whenever(httpException.message()).thenReturn("Not Found")
        whenever(mockContext.getString(R.string.error_network_generic, 404, "Not Found"))
            .thenReturn("Ağ hatası: 404 Not Found")

        // When
        val result = ErrorHelper.createError(httpException, mockContext)

        // Then
        assertTrue(result is Result.Error)
        assertTrue(result.message.contains("404"))
        assertEquals(httpException, result.exception)
    }

    @Test
    fun `createError should return NetworkError for IOException`() {
        // Given
        val ioException = IOException("Connection timeout")

        // When
        val result = ErrorHelper.createError(ioException, mockContext)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Ağ bağlantı hatası", result.message)
        assertEquals(ioException, result.exception)
    }

    @Test
    fun `createError should return UnexpectedError for generic Exception`() {
        // Given
        val exception = Exception("Test error")

        // When
        val result = ErrorHelper.createError(exception, mockContext)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Beklenmeyen hata: Test error", result.message)
        assertEquals(exception, result.exception)
    }

    @Test
    fun `createError should use custom message when provided`() {
        // Given
        val exception = Exception("Original error")
        val customMessage = "Custom error message"

        // When
        val result = ErrorHelper.createError(exception, mockContext, customMessage)

        // Then
        assertTrue(result is Result.Error)
        assertEquals(customMessage, result.message)
        assertEquals(exception, result.exception)
    }

    @Test
    fun `createError should handle exception with null message`() {
        // Given
        val exception = Exception(null as String?)
        whenever(mockContext.getString(R.string.error_unexpected, "Bilinmeyen hata"))
            .thenReturn("Beklenmeyen hata: Bilinmeyen hata")

        // When
        val result = ErrorHelper.createError(exception, mockContext)

        // Then
        assertTrue(result is Result.Error)
        assertNotNull(result.message)
        assertEquals(exception, result.exception)
    }

    @Test
    fun `createHttpError should return HttpError`() {
        // Given - Mock HttpException directly
        val httpException = mock<HttpException>()
        whenever(httpException.code()).thenReturn(500)
        whenever(httpException.message()).thenReturn("Internal Server Error")
        whenever(mockContext.getString(R.string.error_network_generic, 500, "Internal Server Error"))
            .thenReturn("Ağ hatası: 500 Internal Server Error")

        // When
        val result = ErrorHelper.createHttpError(httpException, mockContext)

        // Then
        assertTrue(result is Result.Error)
        assertEquals(httpException, result.exception)
    }

    @Test
    fun `createNetworkError should return NetworkError`() {
        // Given
        val ioException = IOException("Network unreachable")

        // When
        val result = ErrorHelper.createNetworkError(ioException, mockContext)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Ağ bağlantı hatası", result.message)
        assertEquals(ioException, result.exception)
    }

    @Test
    fun `createUnexpectedError should return UnexpectedError`() {
        // Given
        val exception = IllegalStateException("Invalid state")
        whenever(mockContext.getString(R.string.error_unexpected, "Invalid state"))
            .thenReturn("Beklenmeyen hata: Invalid state")

        // When
        val result = ErrorHelper.createUnexpectedError(exception, mockContext)

        // Then
        assertTrue(result is Result.Error)
        assertEquals(exception, result.exception)
    }

    @Test
    fun `createUnexpectedError should use custom message when provided`() {
        // Given
        val exception = Exception("Original")
        val customMessage = "Custom unexpected error"

        // When
        val result = ErrorHelper.createUnexpectedError(exception, mockContext, customMessage)

        // Then
        assertTrue(result is Result.Error)
        assertEquals(customMessage, result.message)
    }

    @Test
    fun `createErrorFromMessage should return Error with message only`() {
        // Given
        val message = "Custom error message"

        // When
        val result = ErrorHelper.createErrorFromMessage(message)

        // Then
        assertTrue(result is Result.Error)
        assertEquals(message, result.message)
        assertTrue(result.exception == null)
    }

    @Test
    fun `createUserNotFoundError should return UserNotFound error`() {
        // When
        val result = ErrorHelper.createUserNotFoundError(mockContext)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Kullanıcı bilgisi bulunamadı veya DNS bilgisi eksik", result.message)
        assertTrue(result.exception == null)
    }

    @Test
    fun `createImagePreloadError should return ImagePreload error`() {
        // Given
        val exception = Exception("Image load failed")
        // Setup was already done in @Before, but ensure it's set
        whenever(mockContext.getString(R.string.error_image_preload, "Image load failed"))
            .thenReturn("Resim ön yükleme hatası: Image load failed")

        // When
        val result = ErrorHelper.createImagePreloadError(exception, mockContext)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Resim ön yükleme hatası: Image load failed", result.message)
        assertEquals(exception, result.exception)
    }

    @Test
    fun `createError should handle different HTTP status codes`() {
        // Given
        val statusCodes = listOf(400, 401, 403, 404, 500, 502, 503)

        statusCodes.forEach { code ->
            val httpException = mock<HttpException>()
            whenever(httpException.code()).thenReturn(code)
            whenever(httpException.message()).thenReturn("Error $code")
            whenever(mockContext.getString(R.string.error_network_generic, code, "Error $code"))
                .thenReturn("Ağ hatası: $code Error $code")

            // When
            val result = ErrorHelper.createError(httpException, mockContext)

            // Then
            assertTrue(result is Result.Error)
            assertEquals(httpException, result.exception)
        }
    }

    @Test
    fun `createError should handle SocketTimeoutException`() {
        // Given
        val timeoutException = java.net.SocketTimeoutException("Connection timed out")

        // When
        val result = ErrorHelper.createError(timeoutException, mockContext)

        // Then - Should be treated as IOException
        assertTrue(result is Result.Error)
        assertEquals("Ağ bağlantı hatası", result.message)
        assertEquals(timeoutException, result.exception)
    }

    @Test
    fun `createError should handle UnknownHostException`() {
        // Given
        val hostException = java.net.UnknownHostException("Unable to resolve host")

        // When
        val result = ErrorHelper.createError(hostException, mockContext)

        // Then - Should be treated as IOException
        assertTrue(result is Result.Error)
        assertEquals("Ağ bağlantı hatası", result.message)
        assertEquals(hostException, result.exception)
    }

    @Test
    fun `createHttpError should return user invalid credentials for 401 when forMainScreenUpdate is true`() {
        // Given
        val httpException = mock<HttpException>()
        whenever(httpException.code()).thenReturn(401)
        whenever(mockContext.getString(R.string.error_user_invalid_credentials))
            .thenReturn("Kullanıcı Bilgileri Hatalı")

        // When
        val result = ErrorHelper.createHttpError(httpException, mockContext, forMainScreenUpdate = true)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Kullanıcı Bilgileri Hatalı", result.message)
        assertEquals(httpException, result.exception)
    }

    @Test
    fun `createHttpError should return user invalid credentials for 403 when forMainScreenUpdate is true`() {
        // Given
        val httpException = mock<HttpException>()
        whenever(httpException.code()).thenReturn(403)
        whenever(mockContext.getString(R.string.error_user_invalid_credentials))
            .thenReturn("Kullanıcı Bilgileri Hatalı")

        // When
        val result = ErrorHelper.createHttpError(httpException, mockContext, forMainScreenUpdate = true)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Kullanıcı Bilgileri Hatalı", result.message)
    }

    @Test
    fun `createHttpError should return server error for 500 when forMainScreenUpdate is true`() {
        // Given
        val httpException = mock<HttpException>()
        whenever(httpException.code()).thenReturn(500)
        whenever(mockContext.getString(R.string.error_server_error))
            .thenReturn("Sunucu Hatası")

        // When
        val result = ErrorHelper.createHttpError(httpException, mockContext, forMainScreenUpdate = true)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Sunucu Hatası", result.message)
    }

    @Test
    fun `createHttpError should return server error for 502 when forMainScreenUpdate is true`() {
        // Given
        val httpException = mock<HttpException>()
        whenever(httpException.code()).thenReturn(502)
        whenever(mockContext.getString(R.string.error_server_error))
            .thenReturn("Sunucu Hatası")

        // When
        val result = ErrorHelper.createHttpError(httpException, mockContext, forMainScreenUpdate = true)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Sunucu Hatası", result.message)
    }

    @Test
    fun `createNetworkError should return no internet when forMainScreenUpdate is true`() {
        // Given
        val ioException = IOException("Network error")
        whenever(mockContext.getString(R.string.error_no_internet))
            .thenReturn("İnternet Bağlantısı Yok")

        // When
        val result = ErrorHelper.createNetworkError(ioException, mockContext, forMainScreenUpdate = true)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("İnternet Bağlantısı Yok", result.message)
        assertEquals(ioException, result.exception)
    }

    @Test
    fun `createNetworkError should use default error when forMainScreenUpdate is false`() {
        // Given
        val ioException = IOException("Network error")

        // When
        val result = ErrorHelper.createNetworkError(ioException, mockContext, forMainScreenUpdate = false)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Ağ bağlantı hatası", result.message)
    }

    @Test
    fun `createUserNotExistsError should return user not exists error`() {
        // Given
        whenever(mockContext.getString(R.string.error_user_not_exists))
            .thenReturn("Kullanıcı Yok")

        // When
        val result = ErrorHelper.createUserNotExistsError(mockContext)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Kullanıcı Yok", result.message)
        assertTrue(result.exception == null)
    }

    @Test
    fun `createUserNotExistsError should return user not selected when forMainScreenUpdate is true`() {
        // Given
        whenever(mockContext.getString(R.string.error_user_not_selected))
            .thenReturn("Kullanıcı Seçili Değil")

        // When
        val result = ErrorHelper.createUserNotFoundError(mockContext, forMainScreenUpdate = true)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Kullanıcı Seçili Değil", result.message)
    }

    @Test
    fun `createUnexpectedError should return error with custom message`() {
        // Given
        val exception = Exception("Unexpected error")
        val customMessage = "Custom error message"

        // When
        val result = ErrorHelper.createUnexpectedError(exception, mockContext, customMessage)

        // Then
        assertTrue(result is Result.Error)
        assertEquals(customMessage, result.message)
        assertEquals(exception, result.exception)
    }
}
