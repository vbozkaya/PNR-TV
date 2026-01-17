package com.pnr.tv.util.error

import android.content.Context
import com.pnr.tv.R
import com.pnr.tv.repository.Result
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/**
 * Hata yönetimi için utility sınıfı.
 * Tutarlı hata mesajları üretir ve Result.Error oluşturmayı kolaylaştırır.
 * Loglama işlemleri ErrorLogger sınıfına taşınmıştır.
 */
object ErrorHelper {

    /**
     * Exception'dan Result.Error oluşturur.
     * Exception tipine göre uygun hata mesajı üretir.
     *
     * @param exception Yakalanan exception
     * @param context Context (string resource'ları için gerekli)
     * @param customMessage Özel hata mesajı (opsiyonel, null ise varsayılan mesaj kullanılır)
     * @param errorContext Hata context (ErrorContext veya String)
     * @return Result.Error
     */
    fun createError(
        exception: Throwable,
        context: Context,
        customMessage: String? = null,
        errorContext: Any? = null,
    ): Result.Error {
        val (message, severity) =
            when (exception) {
                is SocketTimeoutException -> {
                    ErrorLogger.recordError(exception, ErrorCategory.TIMEOUT_ERROR, errorContext)
                    context.getString(R.string.error_timeout_read) to ErrorSeverity.MEDIUM
                }
                is ConnectException -> {
                    ErrorLogger.recordError(exception, ErrorCategory.CONNECTION_ERROR, errorContext)
                    context.getString(R.string.error_timeout_connect) to ErrorSeverity.MEDIUM
                }
                is TimeoutException -> {
                    ErrorLogger.recordError(exception, ErrorCategory.TIMEOUT_ERROR, errorContext)
                    context.getString(R.string.error_timeout) to ErrorSeverity.MEDIUM
                }
                is HttpException -> {
                    ErrorLogger.recordHttpError(exception, errorContext)
                    val httpErrorResult = getHttpErrorMessage(exception, context)
                    httpErrorResult.first to httpErrorResult.second
                }
                is IOException -> {
                    ErrorLogger.recordError(exception, ErrorCategory.NETWORK_ERROR, errorContext)
                    context.getString(R.string.error_network_connection) to ErrorSeverity.MEDIUM
                }
                else -> {
                    ErrorLogger.recordError(exception, ErrorCategory.UNKNOWN_ERROR, errorContext)
                    context.getString(R.string.error_unknown) to ErrorSeverity.MEDIUM
                }
            }

        val finalMessage = customMessage ?: message
        Timber.e(exception, "Error: $finalMessage")
        return Result.Error(message = finalMessage, exception = exception, severity = severity)
    }

    /**
     * HTTP exception için hata mesajı ve severity döndürür.
     */
    private fun getHttpErrorMessage(
        exception: HttpException,
        context: Context,
    ): Pair<String, ErrorSeverity> {
        val httpErrorCode = HttpErrorCode.fromCode(exception.code())
        return if (httpErrorCode != null) {
            val message =
                when (httpErrorCode) {
                    HttpErrorCode.UNAUTHORIZED, HttpErrorCode.FORBIDDEN -> {
                        context.getString(R.string.error_user_invalid_credentials)
                    }
                    HttpErrorCode.INTERNAL_SERVER_ERROR,
                    HttpErrorCode.BAD_GATEWAY,
                    HttpErrorCode.SERVICE_UNAVAILABLE,
                    HttpErrorCode.GATEWAY_TIMEOUT,
                    -> {
                        context.getString(R.string.error_server_unavailable)
                    }
                    HttpErrorCode.NOT_FOUND -> {
                        context.getString(R.string.error_network_generic, exception.code(), "")
                    }
                    else -> {
                        context.getString(R.string.error_network_generic, exception.code(), "")
                    }
                }
            message to httpErrorCode.severity
        } else {
            // Bilinmeyen HTTP kodu
            context.getString(R.string.error_network_generic, exception.code(), "") to ErrorSeverity.MEDIUM
        }
    }

    /**
     * HttpException'dan Result.Error oluşturur.
     * Ana ekran güncelleme için özel hata mesajları kullanır.
     *
     * @param exception HttpException
     * @param context Context
     * @param forMainScreenUpdate Ana ekran güncelleme için mi? (true ise özel mesajlar kullanılır)
     * @param errorContext Hata context (ErrorContext veya String)
     * @return Result.Error
     */
    fun createHttpError(
        exception: HttpException,
        context: Context,
        forMainScreenUpdate: Boolean = false,
        errorContext: Any? = null,
    ): Result.Error {
        if (forMainScreenUpdate) {
            ErrorLogger.recordHttpError(exception, errorContext)
            val (message, severity) = getHttpErrorMessageForMainScreen(exception, context)
            Timber.e(exception, "Error: $message")
            return Result.Error(message = message, exception = exception, severity = severity)
        }
        // createError zaten recordHttpError çağırıyor
        return createError(exception, context, errorContext = errorContext)
    }

    /**
     * Ana ekran güncelleme için HTTP hata mesajı döndürür.
     */
    private fun getHttpErrorMessageForMainScreen(
        exception: HttpException,
        context: Context,
    ): Pair<String, ErrorSeverity> {
        val httpErrorCode = HttpErrorCode.fromCode(exception.code())
        return if (httpErrorCode != null) {
            val message =
                when (httpErrorCode) {
                    HttpErrorCode.UNAUTHORIZED, HttpErrorCode.FORBIDDEN -> {
                        context.getString(R.string.error_user_invalid_credentials)
                    }
                    HttpErrorCode.INTERNAL_SERVER_ERROR,
                    HttpErrorCode.BAD_GATEWAY,
                    HttpErrorCode.SERVICE_UNAVAILABLE,
                    HttpErrorCode.GATEWAY_TIMEOUT,
                    -> {
                        context.getString(R.string.error_server_error)
                    }
                    else -> {
                        context.getString(R.string.error_server_error)
                    }
                }
            message to httpErrorCode.severity
        } else {
            context.getString(R.string.error_server_error) to ErrorSeverity.MEDIUM
        }
    }

    /**
     * IOException'dan Result.Error oluşturur.
     * Ana ekran güncelleme için özel hata mesajları kullanır.
     *
     * @param exception IOException
     * @param context Context
     * @param forMainScreenUpdate Ana ekran güncelleme için mi? (true ise özel mesajlar kullanılır)
     * @param errorContext Hata context (ErrorContext veya String)
     * @return Result.Error
     */
    fun createNetworkError(
        exception: IOException,
        context: Context,
        forMainScreenUpdate: Boolean = false,
        errorContext: Any? = null,
    ): Result.Error {
        if (forMainScreenUpdate) {
            val (message, severity) = getNetworkErrorMessageForMainScreen(exception, context, errorContext)
            Timber.e(exception, "Error: $message")
            return Result.Error(message = message, exception = exception, severity = severity)
        }
        return createError(exception, context, errorContext = errorContext)
    }

    /**
     * Ana ekran güncelleme için network hata mesajı döndürür.
     */
    private fun getNetworkErrorMessageForMainScreen(
        exception: IOException,
        context: Context,
        errorContext: Any?,
    ): Pair<String, ErrorSeverity> {
        return when (exception) {
            is SocketTimeoutException -> {
                ErrorLogger.recordError(exception, ErrorCategory.TIMEOUT_ERROR, errorContext)
                context.getString(R.string.error_timeout_read) to ErrorSeverity.MEDIUM
            }
            is ConnectException -> {
                ErrorLogger.recordError(exception, ErrorCategory.CONNECTION_ERROR, errorContext)
                context.getString(R.string.error_timeout_connect) to ErrorSeverity.MEDIUM
            }
            else -> {
                ErrorLogger.recordError(exception, ErrorCategory.NETWORK_ERROR, errorContext)
                context.getString(R.string.error_no_internet) to ErrorSeverity.MEDIUM
            }
        }
    }

    /**
     * Timeout hatası oluşturur.
     *
     * @param exception Timeout exception
     * @param context Context
     * @param forMainScreenUpdate Ana ekran güncelleme için mi? (kullanılmıyor, geriye dönük uyumluluk için)
     * @param errorContext Hata context (ErrorContext veya String)
     * @return Result.Error
     */
    fun createTimeoutError(
        exception: Exception,
        context: Context,
        @Suppress("UNUSED_PARAMETER") forMainScreenUpdate: Boolean = false,
        errorContext: Any? = null,
    ): Result.Error {
        ErrorLogger.recordError(exception, ErrorCategory.TIMEOUT_ERROR, errorContext)

        val (message, severity) = getTimeoutErrorMessage(exception, context)
        Timber.e(exception, "Timeout error: $message")
        return Result.Error(message = message, exception = exception, severity = severity)
    }

    /**
     * Timeout exception için hata mesajı döndürür.
     */
    private fun getTimeoutErrorMessage(
        exception: Exception,
        context: Context,
    ): Pair<String, ErrorSeverity> {
        return when (exception) {
            is SocketTimeoutException -> {
                context.getString(R.string.error_timeout_read) to ErrorSeverity.MEDIUM
            }
            is ConnectException -> {
                context.getString(R.string.error_timeout_connect) to ErrorSeverity.MEDIUM
            }
            else -> {
                context.getString(R.string.error_timeout) to ErrorSeverity.MEDIUM
            }
        }
    }

    /**
     * Offline durumu için hata mesajı oluşturur.
     *
     * @param context Context
     * @return Result.Error
     */
    fun createOfflineError(context: Context): Result.Error {
        val message = context.getString(R.string.error_offline)
        Timber.w("Offline: $message")
        return Result.Error(message = message, exception = null, severity = ErrorSeverity.LOW)
    }

    /**
     * Genel Exception'dan Result.Error oluşturur.
     *
     * @param exception Exception
     * @param context Context
     * @param customMessage Özel hata mesajı (opsiyonel)
     * @param errorContext Hata context (ErrorContext veya String)
     * @return Result.Error
     */
    fun createUnexpectedError(
        exception: Exception,
        context: Context,
        customMessage: String? = null,
        errorContext: Any? = null,
    ): Result.Error {
        return createError(exception, context, customMessage, errorContext)
    }

    /**
     * Mesaj string'inden Result.Error oluşturur (exception olmadan).
     *
     * @param message Hata mesajı
     * @return Result.Error
     */
    fun createErrorFromMessage(
        message: String,
        severity: ErrorSeverity = ErrorSeverity.MEDIUM,
    ): Result.Error {
        Timber.e("Error: $message")
        return Result.Error(message = message, severity = severity)
    }

    /**
     * Kullanıcı bulunamadı hatası oluşturur.
     *
     * @param context Context
     * @param forMainScreenUpdate Ana ekran güncelleme için mi? (true ise özel mesajlar kullanılır)
     * @return Result.Error
     */
    fun createUserNotFoundError(
        context: Context,
        forMainScreenUpdate: Boolean = false,
    ): Result.Error {
        val message = getUserNotFoundMessage(context, forMainScreenUpdate)
        Timber.e("Error: $message")
        return Result.Error(message = message, severity = ErrorSeverity.MEDIUM)
    }

    /**
     * Kullanıcı bulunamadı hatası için mesaj döndürür.
     */
    private fun getUserNotFoundMessage(
        context: Context,
        forMainScreenUpdate: Boolean,
    ): String {
        return if (forMainScreenUpdate) {
            context.getString(R.string.error_user_not_selected)
        } else {
            context.getString(R.string.error_user_not_found)
        }
    }

    /**
     * Kullanıcı yok hatası oluşturur (hiç kullanıcı eklenmemiş).
     *
     * @param context Context
     * @return Result.Error
     */
    fun createUserNotExistsError(context: Context): Result.Error {
        val message = context.getString(R.string.error_user_not_exists)
        Timber.e("Error: $message")
        return Result.Error(message = message, severity = ErrorSeverity.MEDIUM)
    }

    /**
     * Resim ön yükleme hatası oluşturur.
     *
     * @param exception Exception
     * @param context Context
     * @return Result.Error
     */
    fun createImagePreloadError(
        exception: Exception,
        context: Context,
    ): Result.Error {
        val message =
            context.getString(
                R.string.error_image_preload,
                exception.message ?: context.getString(R.string.error_unknown),
            )
        Timber.e(exception, "Error: $message")
        return Result.Error(message = message, exception = exception, severity = ErrorSeverity.LOW)
    }
}
