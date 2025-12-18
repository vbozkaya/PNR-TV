package com.pnr.tv.repository

import android.content.Context
import com.pnr.tv.R
import com.pnr.tv.di.IptvRetrofit
import com.pnr.tv.extensions.normalizeDnsUrl
import com.pnr.tv.network.ApiService
import com.pnr.tv.repository.Result.Success
import com.pnr.tv.util.ErrorHelper
import com.pnr.tv.util.NetworkUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import retrofit2.HttpException
import retrofit2.Retrofit
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/**
 * Tüm content repository'ler için ortak API çağrı mantığını içeren base sınıf.
 * Retrofit instance yönetimi ve safe API call'ları sağlar.
 */
open class BaseContentRepository(
    @IptvRetrofit protected val retrofitBuilder: Retrofit.Builder,
    protected val userRepository: UserRepository,
    @ApplicationContext protected val context: Context,
) {
    // Retrofit örnekleri için önbellek - Her DNS için tek bir ApiService
    private val apiServiceCache = mutableMapOf<String, ApiService>()

    /**
     * Kullanıcı bilgileriyle birlikte ApiService döndürür.
     * Önbellek kullanarak aynı DNS için tek bir instance oluşturur.
     */
    protected suspend fun getApiServiceWithUser(): Pair<ApiService, com.pnr.tv.db.entity.UserAccountEntity>? {
        val user = userRepository.currentUser.firstOrNull() ?: return null
        // Şifrelenmiş DNS'i çöz
        val encryptedDns = user.dns.trim()
        if (encryptedDns.isEmpty()) return null
        val dns = com.pnr.tv.security.DataEncryption.decryptSensitiveData(encryptedDns, context)

        val baseUrl = dns.normalizeDnsUrl()

        // 1. Önbelleği kontrol et
        val cachedApiService = apiServiceCache[baseUrl]
        if (cachedApiService != null) {
            return Pair(cachedApiService, user)
        }

        // 2. Önbellekte yoksa oluştur, ekle ve döndür
        val newApiService = retrofitBuilder.baseUrl(baseUrl).build().create(ApiService::class.java)
        apiServiceCache[baseUrl] = newApiService
        return Pair(newApiService, user)
    }

    /**
     * Güvenli API çağrısı yapar. Hata durumlarını yakalar ve Result döndürür.
     * Retry mekanizması ve offline detection içerir.
     *
     * @param apiCall API çağrısı lambda'sı
     * @param forMainScreenUpdate Ana ekran güncelleme için mi? (true ise özel hata mesajları kullanılır)
     * @param maxRetries Maksimum retry sayısı (varsayılan: 0, retry yok)
     * @param retryDelayMs Retry arasındaki gecikme (milisaniye, varsayılan: 1000ms)
     * @return Result<T>
     */
    internal suspend fun <T> safeApiCall(
        apiCall: suspend (ApiService, String, String) -> T,
        forMainScreenUpdate: Boolean = false,
        maxRetries: Int = 0,
        retryDelayMs: Long = 1000L,
    ): Result<T> {
        // Offline kontrolü
        if (!NetworkUtils.isNetworkAvailable(context)) {
            Timber.w("Offline mode detected")
            return ErrorHelper.createOfflineError(context)
        }

        var lastException: Exception? = null
        var attempt = 0

        while (attempt <= maxRetries) {
            try {
                // Kullanıcı kontrolü - önce tüm kullanıcıları kontrol et
                val allUsers = userRepository.allUsers.firstOrNull() ?: emptyList()
                if (allUsers.isEmpty()) {
                    Timber.w("Kullanıcı bulunamadı - liste boş")
                    return if (forMainScreenUpdate) {
                        ErrorHelper.createUserNotExistsError(context)
                    } else {
                        ErrorHelper.createUserNotFoundError(context, forMainScreenUpdate)
                    }
                }

                val apiServiceResult = getApiServiceWithUser()
                val (apiService, user) =
                    apiServiceResult ?: run {
                        Timber.w("API Service alınamadı - kullanıcı seçili değil veya DNS boş")
                        return if (forMainScreenUpdate) {
                            ErrorHelper.createUserNotFoundError(context, true)
                        } else {
                            ErrorHelper.createUserNotFoundError(context, false)
                        }
                    }

                // Şifrelenmiş password'u çöz
                val decryptedPassword = com.pnr.tv.security.DataEncryption.decryptSensitiveData(user.password, context)
                val result = apiCall(apiService, user.username, decryptedPassword)
                // Başarılı ise retry yapma
                if (attempt > 0) {
                    Timber.d("Retry successful after $attempt attempts")
                }
                return Success(result)
            } catch (e: HttpException) {
                lastException = e
                Timber.e(e, "❌ HTTP Exception: code=${e.code()}, message=${e.message()}")
                // Response body'yi logla
                try {
                    val errorBody = e.response()?.errorBody()?.string()
                    Timber.e("❌ HTTP Error Body: $errorBody")
                } catch (ex: Exception) {
                    Timber.e(ex, "❌ Error body okunamadı")
                }
                // 4xx hataları için retry yapma (client error)
                if (e.code() in 400..499) {
                    return ErrorHelper.createHttpError(e, context, forMainScreenUpdate, "BaseContentRepository")
                }
                // 5xx hataları için retry yap
                if (attempt < maxRetries && e.code() >= 500) {
                    attempt++
                    Timber.w("⚠️ Server error ${e.code()}, retrying... ($attempt/$maxRetries)")
                    delay(retryDelayMs)
                    continue
                }
                return ErrorHelper.createHttpError(e, context, forMainScreenUpdate, "BaseContentRepository")
            } catch (e: SocketTimeoutException) {
                lastException = e
                Timber.e(e, "⏱️ SocketTimeoutException: ${e.message}")
                if (attempt < maxRetries) {
                    attempt++
                    Timber.w("⏱️ Timeout, retrying... ($attempt/$maxRetries)")
                    delay(retryDelayMs)
                    continue
                }
                return ErrorHelper.createTimeoutError(e, context, forMainScreenUpdate, "BaseContentRepository")
            } catch (e: ConnectException) {
                lastException = e
                Timber.e(e, "🔌 ConnectException: ${e.message}")
                if (attempt < maxRetries) {
                    attempt++
                    Timber.w("🔌 Connection error, retrying... ($attempt/$maxRetries)")
                    delay(retryDelayMs)
                    continue
                }
                return ErrorHelper.createTimeoutError(e, context, forMainScreenUpdate, "BaseContentRepository")
            } catch (e: TimeoutException) {
                lastException = e
                Timber.e(e, "⏱️ TimeoutException: ${e.message}")
                if (attempt < maxRetries) {
                    attempt++
                    Timber.w("⏱️ Timeout exception, retrying... ($attempt/$maxRetries)")
                    delay(retryDelayMs)
                    continue
                }
                return ErrorHelper.createTimeoutError(e, context, forMainScreenUpdate, "BaseContentRepository")
            } catch (e: IOException) {
                lastException = e
                Timber.e(e, "🌐 IOException: ${e.message}")
                // Network hatası için retry yap
                if (attempt < maxRetries) {
                    attempt++
                    Timber.w("🌐 Network error, retrying... ($attempt/$maxRetries)")
                    delay(retryDelayMs)
                    continue
                }
                return ErrorHelper.createNetworkError(e, context, forMainScreenUpdate, "BaseContentRepository")
            } catch (e: Exception) {
                lastException = e
                Timber.e(e, "❌ Beklenmeyen Exception: ${e.javaClass.simpleName} - ${e.message}")
                // Stack trace'i de logla
                e.printStackTrace()
                // Beklenmeyen hatalar için retry yapma
                return if (forMainScreenUpdate) {
                    ErrorHelper.createErrorFromMessage(context.getString(R.string.error_server_error))
                } else {
                    ErrorHelper.createUnexpectedError(e, context, errorContext = "BaseContentRepository")
                }
            }
        }

        // Tüm retry'lar başarısız oldu
        return if (lastException != null) {
            when (lastException) {
                is SocketTimeoutException, is ConnectException, is TimeoutException -> {
                    ErrorHelper.createTimeoutError(lastException, context, forMainScreenUpdate, "BaseContentRepository")
                }
                is IOException -> {
                    ErrorHelper.createNetworkError(lastException, context, forMainScreenUpdate, "BaseContentRepository")
                }
                else -> {
                    ErrorHelper.createErrorFromMessage(context.getString(R.string.error_retry_failed))
                }
            }
        } else {
            ErrorHelper.createErrorFromMessage(context.getString(R.string.error_retry_failed))
        }
    }
}
