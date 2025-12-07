package com.pnr.tv.network

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * IPTV API istekleri arasında rate limiting sağlayan interceptor.
 * Sunucu yükünü azaltmak için ardışık istekler arasında minimum gecikme ekler.
 * 
 * Bu interceptor, ViewModel'deki delay() kullanımını kaldırarak
 * ağ katmanında daha sağlam bir rate limiting mekanizması sağlar.
 */
class RateLimiterInterceptor(
    private val minDelayMs: Long = 500L
) : Interceptor {
    
    private var lastRequestTime: Long = 0
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastRequest = currentTime - lastRequestTime
        
        // Eğer son istekten bu yana yeterli zaman geçmediyse bekle
        if (timeSinceLastRequest < minDelayMs) {
            val waitTime = minDelayMs - timeSinceLastRequest
            Timber.v("⏳ Rate limiter: ${waitTime}ms bekleniyor...")
            Thread.sleep(waitTime)
        }
        
        // İsteği gönder
        val response = chain.proceed(chain.request())
        
        // Son istek zamanını güncelle
        lastRequestTime = System.currentTimeMillis()
        
        return response
    }
}

