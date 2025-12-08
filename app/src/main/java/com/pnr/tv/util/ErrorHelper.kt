package com.pnr.tv.util

import android.content.Context
import com.pnr.tv.R
import com.pnr.tv.repository.Result
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.util.concurrent.TimeoutException

/**
 * Hata yönetimi için utility sınıfı.
 * Tutarlı hata mesajları üretir ve Result.Error oluşturmayı kolaylaştırır.
 */
object ErrorHelper {
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
    ): Result.Error {
        val message =
            customMessage ?: when (exception) {
                is SocketTimeoutException -> {
                    context.getString(R.string.error_timeout_read)
                }
                is ConnectException -> {
                    context.getString(R.string.error_timeout_connect)
                }
                is TimeoutException -> {
                    context.getString(R.string.error_timeout)
                }
                is HttpException -> {
                    context.getString(
                        R.string.error_network_generic,
                        exception.code(),
                        exception.message() ?: "",
                    )
                }
                is IOException -> {
                    context.getString(R.string.error_network_connection)
                }
                else -> {
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
    ): Result.Error {
        if (forMainScreenUpdate) {
            val message = when (exception.code()) {
                401, 403 -> context.getString(R.string.error_user_invalid_credentials)
                500, 502, 503, 504 -> context.getString(R.string.error_server_error)
                else -> context.getString(R.string.error_server_error)
            }
            Timber.e(exception, "Error: $message")
            return Result.Error(message = message, exception = exception)
        }
        return createError(exception, context)
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
    ): Result.Error {
        if (forMainScreenUpdate) {
            val message = when (exception) {
                is SocketTimeoutException -> context.getString(R.string.error_timeout_read)
                is ConnectException -> context.getString(R.string.error_timeout_connect)
                else -> context.getString(R.string.error_no_internet)
            }
            Timber.e(exception, "Error: $message")
            return Result.Error(message = message, exception = exception)
        }
        return createError(exception, context)
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
    ): Result.Error {
        val message = when (exception) {
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
    ): Result.Error {
        return createError(exception, context, customMessage)
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
        val message = if (forMainScreenUpdate) {
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



