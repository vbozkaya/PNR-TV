package com.pnr.tv.util.error

/**
 * Hata şiddeti seviyeleri.
 * Hataların kullanıcıya ne kadar süre gösterileceğini belirler.
 */
enum class ErrorSeverity(
    /**
     * Hatanın otomatik kapanma süresi (milisaniye).
     * LOW: 3 saniye (kısa, geçici hatalar)
     * MEDIUM: 5 saniye (orta, retry edilebilir hatalar)
     * HIGH: 8 saniye (uzun, önemli hatalar)
     * CRITICAL: 0 (kapanmaz, kullanıcı manuel kapatmalı)
     */
    val autoDismissDurationMs: Long,
) {
    /**
     * Düşük şiddet - Kullanıcı etkilenmez, otomatik recover.
     * Örnek: Geçici network hatası, cache'den veri gösterilebilir.
     */
    LOW(3000L),

    /**
     * Orta şiddet - Kullanıcı etkilenir, retry ile çözülebilir.
     * Örnek: Timeout hatası, network connection hatası.
     */
    MEDIUM(5000L),

    /**
     * Yüksek şiddet - Kullanıcı etkilenir, manuel müdahale gerekebilir.
     * Örnek: Server hatası (500, 502, 503, 504), authentication hatası.
     */
    HIGH(8000L),

    /**
     * Kritik şiddet - Uygulama çökebilir, acil müdahale gerekli.
     * Otomatik kapanmaz, kullanıcı manuel kapatmalı.
     * Örnek: Database corruption, critical system error.
     */
    CRITICAL(0L), // 0 = kapanmaz
    ;

    /**
     * Hatanın otomatik kapanıp kapanmayacağını kontrol eder.
     */
    val shouldAutoDismiss: Boolean
        get() = autoDismissDurationMs > 0
}
