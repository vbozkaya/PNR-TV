package com.pnr.tv.util.error

import timber.log.Timber

/**
 * Circuit Breaker Pattern implementasyonu.
 * Sürekli başarısız olan servisler için istekleri geçici olarak engeller.
 *
 * Circuit Breaker 3 state'de çalışır:
 * - CLOSED: Normal çalışma, tüm istekler kabul edilir
 * - OPEN: Hata eşiği aşıldı, istekler reddediliyor
 * - HALF_OPEN: Test modu, sınırlı sayıda istek kabul ediliyor
 */
class CircuitBreaker(
    /**
     * Circuit breaker'ın OPEN state'e geçmesi için gereken başarısızlık sayısı.
     */
    private val failureThreshold: Int = 5,
    /**
     * OPEN state'den HALF_OPEN state'e geçmek için bekleme süresi (milisaniye).
     */
    private val recoveryTimeout: Long = 60000L, // 1 dakika
    /**
     * HALF_OPEN state'de kabul edilecek maksimum istek sayısı.
     * Bu kadar başarılı istek olursa CLOSED state'e geçer.
     */
    private val halfOpenMaxCalls: Int = 3,
) {
    /**
     * Circuit breaker state'leri.
     */
    enum class CircuitState {
        /**
         * Normal çalışma - Tüm istekler kabul edilir.
         */
        CLOSED,

        /**
         * Hata eşiği aşıldı - İstekler reddediliyor.
         */
        OPEN,

        /**
         * Test modu - Sınırlı sayıda istek kabul ediliyor.
         */
        HALF_OPEN,
    }

    private var failureCount = 0
    private var lastFailureTime = 0L
    private var state = CircuitState.CLOSED
    private var halfOpenSuccessCount = 0

    /**
     * İsteğin kabul edilip edilmeyeceğini kontrol eder.
     *
     * @return true ise istek kabul edilir, false ise reddedilir
     */
    fun shouldAllowRequest(): Boolean {
        return when (state) {
            CircuitState.CLOSED -> {
                // Normal durum - tüm istekler kabul edilir
                true
            }
            CircuitState.OPEN -> {
                // Recovery timeout geçtiyse HALF_OPEN state'e geç
                if (System.currentTimeMillis() - lastFailureTime > recoveryTimeout) {
                    state = CircuitState.HALF_OPEN
                    halfOpenSuccessCount = 0
                    Timber.i("🔄 Circuit breaker HALF_OPEN state'e geçti - Test modu")
                    true
                } else {
                    // Hala OPEN state'de - istekler reddediliyor
                    false
                }
            }
            CircuitState.HALF_OPEN -> {
                // HALF_OPEN state'de sınırlı sayıda istek kabul edilir
                halfOpenSuccessCount < halfOpenMaxCalls
            }
        }
    }

    /**
     * Başarılı isteği kaydeder.
     * HALF_OPEN state'de yeterli başarılı istek olursa CLOSED state'e geçer.
     */
    fun recordSuccess() {
        when (state) {
            CircuitState.CLOSED -> {
                // Normal durum - bir şey yapma
            }
            CircuitState.OPEN -> {
                // Bu durumda success kaydedilmemeli (shouldAllowRequest false döndürüyor)
            }
            CircuitState.HALF_OPEN -> {
                halfOpenSuccessCount++
                if (halfOpenSuccessCount >= halfOpenMaxCalls) {
                    // Yeterli başarılı istek - CLOSED state'e geç
                    state = CircuitState.CLOSED
                    failureCount = 0
                    Timber.i("✅ Circuit breaker CLOSED state'e geçti - Servis kurtarıldı")
                }
            }
        }
    }

    /**
     * Başarısız isteği kaydeder.
     * Yeterli başarısızlık olursa OPEN state'e geçer.
     */
    fun recordFailure() {
        failureCount++
        lastFailureTime = System.currentTimeMillis()

        when (state) {
            CircuitState.CLOSED -> {
                // Başarısızlık sayısı eşiği aştıysa OPEN state'e geç
                if (failureCount >= failureThreshold) {
                    state = CircuitState.OPEN
                    Timber.w("⚠️ Circuit breaker OPEN state'e geçti - Çok fazla hata ($failureCount)")
                }
            }
            CircuitState.OPEN -> {
                // Zaten OPEN state'de - bir şey yapma
            }
            CircuitState.HALF_OPEN -> {
                // HALF_OPEN state'de başarısızlık - tekrar OPEN state'e geç
                state = CircuitState.OPEN
                halfOpenSuccessCount = 0
                Timber.w("⚠️ Circuit breaker OPEN state'e geçti - HALF_OPEN state'de başarısızlık")
            }
        }
    }

    /**
     * Mevcut state'i döndürür.
     */
    fun getState(): CircuitState = state

    /**
     * Başarısızlık sayısını döndürür.
     */
    fun getFailureCount(): Int = failureCount

    /**
     * Circuit breaker'ı sıfırlar (test için).
     */
    fun reset() {
        state = CircuitState.CLOSED
        failureCount = 0
        lastFailureTime = 0L
        halfOpenSuccessCount = 0
    }
}
