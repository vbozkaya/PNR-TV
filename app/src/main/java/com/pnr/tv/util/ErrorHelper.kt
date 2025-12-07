package com.pnr.tv.util

import android.content.Context
import com.pnr.tv.R
import com.pnr.tv.repository.Result
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

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
     *
     * @param exception HttpException
     * @param context Context
     * @return Result.Error
     */
    fun createHttpError(
        exception: HttpException,
        context: Context,
    ): Result.Error {
        return createError(exception, context)
    }

    /**
     * IOException'dan Result.Error oluşturur.
     *
     * @param exception IOException
     * @param context Context
     * @return Result.Error
     */
    fun createNetworkError(
        exception: IOException,
        context: Context,
    ): Result.Error {
        return createError(exception, context)
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
     * @return Result.Error
     */
    fun createUserNotFoundError(context: Context): Result.Error {
        val message = context.getString(R.string.error_user_not_found)
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



