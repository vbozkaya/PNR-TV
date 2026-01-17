package com.pnr.tv.repository

import android.content.Context
import com.pnr.tv.R
import com.pnr.tv.network.ApiService
import com.pnr.tv.network.dto.AuthenticationResponseDto
import com.pnr.tv.repository.Result.Success
import com.pnr.tv.util.error.ErrorContext
import com.pnr.tv.util.error.ErrorHelper
import com.pnr.tv.util.error.HttpErrorCode
import com.pnr.tv.util.network.NetworkUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import javax.inject.Inject

/**
 * Tüm content repository'ler için ortak API çağrı mantığını içeren base sınıf.
 * Safe API call'ları ve kategori refresh işlemlerini sağlar.
 */
open class BaseContentRepository @Inject constructor(
    protected val apiServiceManager: ApiServiceManager,
    protected val userRepository: UserRepository,
    @ApplicationContext protected val context: Context,
) {

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
            Timber.w("🌐 Offline mode detected - Cache'den veri gösterilecek")
            // Offline durumunda cache'den veri gösterilecek (ViewModel katmanında Flow'lar zaten cache'den geliyor)
            // Burada sadece warning döndürüyoruz, ViewModel'ler cache'den veri göstermeye devam edecek
            return ErrorHelper.createOfflineError(context)
        }

        var lastException: Exception? = null
        var attempt = 0

        // DNS bilgisini al (circuit breaker için)
        val apiServiceResult = apiServiceManager.getApiServiceWithUser()
        val (apiService, user) =
            apiServiceResult ?: run {
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
                Timber.w("API Service alınamadı - kullanıcı seçili değil veya DNS boş")
                return if (forMainScreenUpdate) {
                    ErrorHelper.createUserNotFoundError(context, true)
                } else {
                    ErrorHelper.createUserNotFoundError(context, false)
                }
            }

        // DNS için circuit breaker al
        val baseUrl = apiServiceManager.getBaseUrlFromUser(user)
            ?: run {
                Timber.w("Base URL alınamadı")
                return ErrorHelper.createUserNotFoundError(context, forMainScreenUpdate)
            }
        val circuitBreaker = apiServiceManager.getCircuitBreaker(baseUrl)

        while (attempt <= maxRetries) {
            // Circuit breaker kontrolü
            if (!circuitBreaker.shouldAllowRequest()) {
                val errorContext =
                    ErrorContext.Builder()
                        .setRepository("BaseContentRepository")
                        .setOperation("executeWithRetry")
                        .addInfo("circuit_breaker_state", circuitBreaker.getState().name)
                        .build()
                Timber.w("🚫 Circuit breaker OPEN - İstek reddedildi (state: ${circuitBreaker.getState()})")
                return ErrorHelper.createError(
                    exception = Exception("Circuit breaker is OPEN"),
                    context = context,
                    customMessage = context.getString(R.string.error_server_unavailable),
                    errorContext = errorContext,
                )
            }

            try {
                // Şifrelenmiş password'u çöz
                val decryptedPassword = com.pnr.tv.security.DataEncryption.decryptSensitiveData(user.password, context)
                val result = apiCall(apiService, user.username, decryptedPassword)

                // Başarılı ise circuit breaker'a bildir
                circuitBreaker.recordSuccess()

                // Başarılı ise retry yapma
                if (attempt > 0) {
                }
                return Success(result)
            } catch (e: HttpException) {
                lastException = e
                Timber.tag("DB_DEBUG").e(e, "❌ HTTP Exception: code=${e.code()}, message=${e.message()}")
                Timber.tag("DB_DEBUG").e("❌ HTTP Response Code: ${e.code()}")
                Timber.tag("DB_DEBUG").e("❌ HTTP Request URL: ${e.response()?.raw()?.request?.url}")
                Timber.tag("DB_DEBUG").e("❌ HTTP Request Method: ${e.response()?.raw()?.request?.method}")
                // Response body'yi logla
                try {
                    val errorBody = e.response()?.errorBody()?.string()
                    Timber.tag("DB_DEBUG").e("❌ HTTP Error Body: $errorBody")
                } catch (ex: Exception) {
                    Timber.tag("DB_DEBUG").e(ex, "❌ Error body okunamadı")
                }

                // ErrorContext oluştur
                val errorContext =
                    ErrorContext.Builder()
                        .setRepository("BaseContentRepository")
                        .setOperation("executeWithRetry")
                        .addInfo("attempt", attempt.toString())
                        .addInfo("maxRetries", maxRetries.toString())
                        .build()

                // HttpErrorCode enum'unu kullanarak retry mantığını belirle
                val httpErrorCode = HttpErrorCode.fromCode(e.code())
                if (httpErrorCode != null && !HttpErrorCode.isRetryable(e.code())) {
                    // Retry edilemeyen hatalar (4xx - client errors) için direkt error döndür
                    return ErrorHelper.createHttpError(e, context, forMainScreenUpdate, errorContext)
                }
                // Circuit breaker'a başarısızlığı bildir
                circuitBreaker.recordFailure()

                // Retry edilebilir hatalar (5xx - server errors, 408, 429) için retry yap
                if (attempt < maxRetries && HttpErrorCode.isRetryable(e.code())) {
                    attempt++
                    val backoffDelay = NetworkPolicyHelper.calculateExponentialBackoffDelay(attempt, retryDelayMs)
                    Timber.w("⚠️ Server error ${e.code()}, retrying... ($attempt/$maxRetries) after ${backoffDelay}ms")
                    delay(backoffDelay)
                    continue
                }
                return ErrorHelper.createHttpError(e, context, forMainScreenUpdate, errorContext)
            } catch (e: SocketTimeoutException) {
                lastException = e
                // Circuit breaker'a başarısızlığı bildir
                circuitBreaker.recordFailure()
                Timber.e(e, "⏱️ SocketTimeoutException: ${e.message}")
                if (attempt < maxRetries) {
                    attempt++
                    val backoffDelay = NetworkPolicyHelper.calculateExponentialBackoffDelay(attempt, retryDelayMs)
                    Timber.w("⏱️ Timeout, retrying... ($attempt/$maxRetries) after ${backoffDelay}ms")
                    delay(backoffDelay)
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
                // Circuit breaker'a başarısızlığı bildir
                circuitBreaker.recordFailure()
                Timber.e(e, "🌐 IOException: ${e.message}")
                // Network hatası için retry yap
                if (attempt < maxRetries) {
                    attempt++
                    val backoffDelay = NetworkPolicyHelper.calculateExponentialBackoffDelay(attempt, retryDelayMs)
                    Timber.w("🌐 Network error, retrying... ($attempt/$maxRetries) after ${backoffDelay}ms")
                    delay(backoffDelay)
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

    /**
     * Generic kategori refresh helper metodu.
     * Tüm kategori refresh işlemlerinde ortak olan mantığı içerir:
     * - API çağrısı
     * - DTO'dan Entity'ye dönüştürme
     * - Veritabanına kaydetme (clearAll + insertAll)
     * - 500 hatası handling
     *
     * @param apiCall API'den kategori DTO'larını getiren lambda
     * @param entityMapper DTO'yu Entity'ye dönüştüren lambda
     * @param daoClearAll DAO'nun clearAll metodunu çağıran lambda
     * @param daoInsertAll DAO'nun insertAll metodunu çağıran lambda
     * @param daoGetAll DAO'nun getAll metodunu çağıran lambda (500 hatası için, kullanılmıyor)
     * @return Result<Unit>
     */
    protected suspend fun <TDto, TEntity> refreshCategories(
        apiCall: suspend (ApiService, String, String) -> List<TDto>,
        entityMapper: (List<TDto>) -> List<TEntity>,
        daoClearAll: suspend () -> Unit,
        daoInsertAll: suspend (List<TEntity>) -> Unit,
        @Suppress("UNUSED_PARAMETER") daoGetAll: suspend () -> kotlinx.coroutines.flow.Flow<List<*>>,
    ): Result<Unit> {
        val result =
            safeApiCall(
                apiCall = { api, user, pass ->
                    val categoriesDto = apiCall(api, user, pass)
                    val entities = entityMapper(categoriesDto)

                    // API BAŞARILI OLDU - ŞİMDİ SİL VE EKLE
                    try {
                        daoClearAll()

                        if (entities.isNotEmpty()) {
                            daoInsertAll(entities)
                        }
                    } catch (e: Exception) {
                        Timber.tag("DB_DEBUG").e(e, "!!! KATEGORİ GÜNCELLEME HATASI: ${e.message}")
                        throw e
                    }
                },
            )

        // 500 hatası handling - ortak mantık
        return handle500ErrorForCategories(result, daoClearAll)
    }

    /**
     * 500 hatası için kategori silme helper metodu.
     * Bazı API'ler 0 kategori döndürdüğünde 500 hatası verebilir.
     * Bu durumda kategorileri silip başarılı olarak döndürür.
     *
     * @param result API çağrısının sonucu
     * @param daoClearAll DAO'nun clearAll metodunu çağıran lambda
     * @return Result<Unit>
     */
    private suspend fun handle500ErrorForCategories(
        result: Result<Unit>,
        daoClearAll: suspend () -> Unit,
    ): Result<Unit> {
        if (result is Result.Error) {
            Timber.tag("DB_DEBUG").w("⚠️  API çağrısı başarısız: ${result.message}")

            // 500 hatası veriyorsa, muhtemelen 0 kategori durumu - kategorileri sil
            val errorMessage = result.message ?: ""
            if (errorMessage.contains("500") || errorMessage.contains("Internal Server Error")) {
                Timber.tag("DB_DEBUG").w("⚠️  500 hatası tespit edildi - muhtemelen 0 kategori durumu, kategoriler siliniyor...")
                try {
                    daoClearAll()
                    // Başarılı olarak döndür (kategoriler silindi)
                    return Result.Success(Unit)
                } catch (e: Exception) {
                    Timber.tag("DB_DEBUG").e(e, "!!! 500 hatası durumunda kategori silme hatası: ${e.message}")
                    // Hata olsa bile orijinal hatayı döndür
                }
            }
        }

        return result
    }

    /**
     * Kullanıcı bilgilerini getirir.
     * Bu metod safeApiCall kullanır.
     */
    suspend fun fetchUserInfo(): Result<AuthenticationResponseDto> {
        return safeApiCall(apiCall = { api, user, pass ->
            try {
                api.getUserInfo(user, pass)
            } catch (e: com.squareup.moshi.JsonDataException) {
                Timber.e(e, "JSON Parse Hatası - API response formatı beklenenle uyuşmuyor: ${e.message}")
                throw e
            } catch (e: Exception) {
                Timber.e(e, "API çağrısı sırasında beklenmeyen hata: ${e.javaClass.simpleName} - ${e.message}")
                throw e
            }
        })
    }
}
