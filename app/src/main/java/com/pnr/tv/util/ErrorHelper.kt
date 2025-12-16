package com.pnr.tv.util

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pnr.tv.R
import com.pnr.tv.repository.Result
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/**
 * Hata kategorileri - Crashlytics sınıflandırması için
 * NOT: Kullanıcı verileri (PII) kesinlikle eklenmez
 */
object ErrorCategory {
    const val NETWORK_ERROR = "network_error"
    const val CONNECTION_ERROR = "connection_error"
    const val TIMEOUT_ERROR = "timeout_error"
    const val HTTP_ERROR = "http_error"
    const val DATABASE_ERROR = "database_error"
    const val UI_ERROR = "ui_error"
    const val PLAYER_ERROR = "player_error"
    const val UNKNOWN_ERROR = "unknown_error"
}

/**
 * Hata yönetimi için utility sınıfı.
 * Tutarlı hata mesajları üretir ve Result.Error oluşturmayı kolaylaştırır.
 * Crashlytics'e güvenli sınıflandırma bilgileri ekler (PII içermez).
 */
object ErrorHelper {
    /**
     * Crashlytics'e güvenli hata bilgileri ekler.
     * NOT: Kullanıcı verileri (username, password, DNS, email) kesinlikle eklenmez.
     *
     * @param exception Exception
     * @param errorCategory Hata kategorisi (ErrorCategory'dan)
     * @param errorContext Hatanın oluştuğu context (sadece class name, örn: "BaseContentRepository")
     */
    private fun recordErrorToCrashlytics(
        exception: Throwable,
        errorCategory: String,
        errorContext: String? = null,
    ) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()

            // Hata kategorisi
            crashlytics.setCustomKey("error_category", errorCategory)

            // Hata oluştuğu context (sadece class name, kişisel bilgi yok)
            errorContext?.let {
                crashlytics.setCustomKey("error_context", it)
            }

            // Exception tipi
            crashlytics.setCustomKey("exception_type", exception.javaClass.simpleName)

            // Exception'ı kaydet
            crashlytics.recordException(exception)

            // Log mesajı (kişisel bilgi içermez)
            crashlytics.log("Error in $errorCategory${errorContext?.let { " at $it" } ?: ""}: ${exception.javaClass.simpleName}")
        } catch (e: Exception) {
            // Crashlytics'e kaydetme hatası - sessizce geç
            Timber.e(e, "Crashlytics'e hata kaydedilemedi")
        }
    }

    /**
     * Exception'dan Result.Error oluşturur.
     * Exception tipine göre uygun hata mesajı üretir.
     *
     * @param exception Yakalanan exception
     * @param context Context (string resource'ları için gerekli)
     * @param customMessage Özel hata mesajı (opsiyonel, null ise varsayılan mesaj kullanılır)
     * @return Result.Error
     */
    fun createError(
        exception: Throwable,
        context: Context,
        customMessage: String? = null,
        errorContext: String? = null,
    ): Result.Error {
        val message =
            customMessage ?: when (exception) {
                is SocketTimeoutException -> {
                    recordErrorToCrashlytics(exception, ErrorCategory.TIMEOUT_ERROR, errorContext)
                    context.getString(R.string.error_timeout_read)
                }
                is ConnectException -> {
                    recordErrorToCrashlytics(exception, ErrorCategory.CONNECTION_ERROR, errorContext)
                    context.getString(R.string.error_timeout_connect)
                }
                is TimeoutException -> {
                    recordErrorToCrashlytics(exception, ErrorCategory.TIMEOUT_ERROR, errorContext)
                    context.getString(R.string.error_timeout)
                }
                is HttpException -> {
                    recordErrorToCrashlytics(exception, ErrorCategory.HTTP_ERROR, errorContext)
                    context.getString(
                        R.string.error_network_generic,
                        exception.code(),
                        exception.message() ?: "",
                    )
                }
                is IOException -> {
                    recordErrorToCrashlytics(exception, ErrorCategory.NETWORK_ERROR, errorContext)
                    context.getString(R.string.error_network_connection)
                }
                else -> {
                    recordErrorToCrashlytics(exception, ErrorCategory.UNKNOWN_ERROR, errorContext)
                    context.getString(
                        R.string.error_unexpected,
                        exception.message ?: context.getString(R.string.error_unknown),
                    )
                }
            }

        Timber.e(exception, "Error: $message")
        return Result.Error(message = message, exception = exception)
    }

    /**
     * HttpException'dan Result.Error oluşturur.
     * Ana ekran güncelleme için özel hata mesajları kullanır.
     *
     * @param exception HttpException
     * @param context Context
     * @param forMainScreenUpdate Ana ekran güncelleme için mi? (true ise özel mesajlar kullanılır)
     * @return Result.Error
     */
    fun createHttpError(
        exception: HttpException,
        context: Context,
        forMainScreenUpdate: Boolean = false,
        errorContext: String? = null,
    ): Result.Error {
        // HTTP status code'u ekle (güvenli, kişisel bilgi değil)
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("http_status_code", exception.code())
        } catch (e: Exception) {
            // Sessizce geç
        }

        if (forMainScreenUpdate) {
            val message =
                when (exception.code()) {
                    401, 403 -> context.getString(R.string.error_user_invalid_credentials)
                    500, 502, 503, 504 -> context.getString(R.string.error_server_error)
                    else -> context.getString(R.string.error_server_error)
                }
            recordErrorToCrashlytics(exception, ErrorCategory.HTTP_ERROR, errorContext)
            Timber.e(exception, "Error: $message")
            return Result.Error(message = message, exception = exception)
        }
        return createError(exception, context, errorContext = errorContext)
    }

    /**
     * IOException'dan Result.Error oluşturur.
     * Ana ekran güncelleme için özel hata mesajları kullanır.
     *
     * @param exception IOException
     * @param context Context
     * @param forMainScreenUpdate Ana ekran güncelleme için mi? (true ise özel mesajlar kullanılır)
     * @return Result.Error
     */
    fun createNetworkError(
        exception: IOException,
        context: Context,
        forMainScreenUpdate: Boolean = false,
        errorContext: String? = null,
    ): Result.Error {
        if (forMainScreenUpdate) {
            val message =
                when (exception) {
                    is SocketTimeoutException -> {
                        recordErrorToCrashlytics(exception, ErrorCategory.TIMEOUT_ERROR, errorContext)
                        context.getString(R.string.error_timeout_read)
                    }
                    is ConnectException -> {
                        recordErrorToCrashlytics(exception, ErrorCategory.CONNECTION_ERROR, errorContext)
                        context.getString(R.string.error_timeout_connect)
                    }
                    else -> {
                        recordErrorToCrashlytics(exception, ErrorCategory.NETWORK_ERROR, errorContext)
                        context.getString(R.string.error_no_internet)
                    }
                }
            Timber.e(exception, "Error: $message")
            return Result.Error(message = message, exception = exception)
        }
        return createError(exception, context, errorContext = errorContext)
    }

    /**
     * Timeout hatası oluşturur.
     *
     * @param exception Timeout exception
     * @param context Context
     * @param forMainScreenUpdate Ana ekran güncelleme için mi?
     * @return Result.Error
     */
    fun createTimeoutError(
        exception: Exception,
        context: Context,
        forMainScreenUpdate: Boolean = false,
        errorContext: String? = null,
    ): Result.Error {
        recordErrorToCrashlytics(exception, ErrorCategory.TIMEOUT_ERROR, errorContext)

        val message =
            when (exception) {
                is SocketTimeoutException -> {
                    if (forMainScreenUpdate) {
                        context.getString(R.string.error_timeout_read)
                    } else {
                        context.getString(R.string.error_timeout_read)
                    }
                }
                is ConnectException -> {
                    if (forMainScreenUpdate) {
                        context.getString(R.string.error_timeout_connect)
                    } else {
                        context.getString(R.string.error_timeout_connect)
                    }
                }
                else -> {
                    if (forMainScreenUpdate) {
                        context.getString(R.string.error_timeout)
                    } else {
                        context.getString(R.string.error_timeout)
                    }
                }
            }
        Timber.e(exception, "Timeout error: $message")
        return Result.Error(message = message, exception = exception)
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
        return Result.Error(message = message, exception = null)
    }

    /**
     * Genel Exception'dan Result.Error oluşturur.
     *
     * @param exception Exception
     * @param context Context
     * @param customMessage Özel hata mesajı (opsiyonel)
     * @return Result.Error
     */
    fun createUnexpectedError(
        exception: Exception,
        context: Context,
        customMessage: String? = null,
        errorContext: String? = null,
    ): Result.Error {
        return createError(exception, context, customMessage, errorContext)
    }

    /**
     * Mesaj string'inden Result.Error oluşturur (exception olmadan).
     *
     * @param message Hata mesajı
     * @return Result.Error
     */
    fun createErrorFromMessage(message: String): Result.Error {
        Timber.e("Error: $message")
        return Result.Error(message = message)
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
        val message =
            if (forMainScreenUpdate) {
                context.getString(R.string.error_user_not_selected)
            } else {
                context.getString(R.string.error_user_not_found)
            }
        Timber.e("Error: $message")
        return Result.Error(message = message)
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
        return Result.Error(message = message)
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
        return Result.Error(message = message, exception = exception)
    }
}
