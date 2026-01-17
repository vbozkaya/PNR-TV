package com.pnr.tv.repository

import android.content.Context
import android.util.LruCache
import com.pnr.tv.di.IptvRetrofit
import com.pnr.tv.extensions.normalizeDnsUrl
import com.pnr.tv.network.ApiService
import com.pnr.tv.util.error.CircuitBreaker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API Service ve Circuit Breaker yönetimi için singleton sınıf.
 * DNS bazlı ApiService cache'i ve Circuit Breaker'ları yönetir.
 */
@Singleton
class ApiServiceManager @Inject constructor(
    @IptvRetrofit private val retrofitBuilder: Retrofit.Builder,
    private val userRepository: UserRepository,
    @ApplicationContext private val context: Context,
) {
    // Retrofit örnekleri için önbellek - Her DNS için tek bir ApiService
    // LruCache kullanarak bellek sızıntısını önle - maksimum 10 API servisi tutulur
    private val apiServiceCache =
        object : LruCache<String, ApiService>(10) {
            override fun sizeOf(
                key: String,
                value: ApiService,
            ): Int {
                // Her entry için 1 birim sayılır
                return 1
            }
        }

    // Circuit breaker - Her DNS için ayrı circuit breaker
    // LruCache kullanarak bellek sızıntısını önle - maksimum 10 circuit breaker tutulur
    private val circuitBreakers =
        object : LruCache<String, CircuitBreaker>(10) {
            override fun sizeOf(
                key: String,
                value: CircuitBreaker,
            ): Int {
                // Her entry için 1 birim sayılır
                return 1
            }
        }

    /**
     * DNS için circuit breaker alır veya oluşturur.
     * LruCache kullanarak bellek yönetimi sağlanır.
     *
     * @param baseUrl DNS base URL'i
     * @return CircuitBreaker instance
     */
    fun getCircuitBreaker(baseUrl: String): CircuitBreaker {
        // LruCache'den al
        val cached = circuitBreakers.get(baseUrl)
        if (cached != null) {
            return cached
        }

        // Cache'de yoksa yeni oluştur ve ekle
        val newCircuitBreaker =
            CircuitBreaker(
                failureThreshold = 5,
                recoveryTimeout = 60000L, // 1 dakika
                halfOpenMaxCalls = 3,
            )
        circuitBreakers.put(baseUrl, newCircuitBreaker)
        return newCircuitBreaker
    }

    /**
     * Kullanıcı bilgileriyle birlikte ApiService döndürür.
     * LruCache kullanarak aynı DNS için tek bir instance oluşturur ve bellek yönetimi sağlar.
     *
     * @return Pair<ApiService, UserAccountEntity> veya null
     */
    suspend fun getApiServiceWithUser(): Pair<ApiService, com.pnr.tv.db.entity.UserAccountEntity>? {
        val user = userRepository.currentUser.firstOrNull() ?: return null
        // Şifrelenmiş DNS'i çöz
        val encryptedDns = user.dns.trim()
        if (encryptedDns.isEmpty()) return null
        val dns = com.pnr.tv.security.DataEncryption.decryptSensitiveData(encryptedDns, context)

        val baseUrl = dns.normalizeDnsUrl()

        // 1. LruCache'den kontrol et
        val cachedApiService = apiServiceCache.get(baseUrl)
        if (cachedApiService != null) {
            return Pair(cachedApiService, user)
        }

        // 2. Cache'de yoksa oluştur, ekle ve döndür
        val newApiService = retrofitBuilder.baseUrl(baseUrl).build().create(ApiService::class.java)
        apiServiceCache.put(baseUrl, newApiService)
        return Pair(newApiService, user)
    }

    /**
     * Kullanıcıdan DNS bilgisini alır ve normalize eder.
     *
     * @param user Kullanıcı entity
     * @return Normalize edilmiş base URL veya null
     */
    fun getBaseUrlFromUser(user: com.pnr.tv.db.entity.UserAccountEntity): String? {
        val encryptedDns = user.dns.trim()
        if (encryptedDns.isEmpty()) return null
        val dns = com.pnr.tv.security.DataEncryption.decryptSensitiveData(encryptedDns, context)
        return dns.normalizeDnsUrl()
    }
}
