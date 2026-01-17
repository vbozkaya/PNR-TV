package com.pnr.tv.security

import com.pnr.tv.BuildConfig

/**
 * Certificate Pinning yapılandırması.
 *
 * MITM (Man-in-the-Middle) saldırılarına karşı koruma sağlar.
 *
 * Certificate pin'lerini almak için:
 *
 * **TMDB API için:**
 * ```bash
 * echo | openssl s_client -servername api.themoviedb.org -connect api.themoviedb.org:443 2>/dev/null | \
 *   openssl x509 -pubkey -noout | \
 *   openssl pkey -pubin -outform der | \
 *   openssl dgst -sha256 -binary | \
 *   base64
 * ```
 *
 * **Windows PowerShell için:**
 * ```powershell
 * $cert = [System.Net.ServicePointManager]::ServerCertificateValidationCallback = {$true}
 * $request = [System.Net.WebRequest]::Create("https://api.themoviedb.org")
 * $request.GetResponse()
 * # Certificate bilgilerini almak için farklı bir yöntem gerekebilir
 * ```
 *
 * **Alternatif (Online Tool):**
 * https://www.ssllabs.com/ssltest/analyze.html?d=api.themoviedb.org
 *
 * **Not:** Production build'de certificate pinning aktif, debug build'de devre dışı.
 * Bu, geliştirme sırasında farklı sertifikalar kullanılabilmesi için.
 */
object CertificatePinningConfig {
    /**
     * TMDB API için certificate pin'leri.
     *
     * Production build'de kullanılır.
     *
     * **ÖNEMLİ:** Placeholder pin'leri gerçek pin'lerle değiştirin!
     * Gerçek pin'leri almak için yukarıdaki komutları kullanın.
     */
    private val TMDB_PINS =
        if (BuildConfig.IS_PRODUCTION) {
            listOf(
                // TMDB API için certificate pin
                // SSL Labs'tan alındı: https://www.ssllabs.com/ssltest/analyze.html?d=api.themoviedb.org
                // Certificate: *.themoviedb.org (Amazon RSA 2048 M04)
                // Pin SHA256: f78NVAesYtdZ9OGSbK7VtGQkSIVykh3DnduuLIJHMu4=
                // Valid until: Fri, 17 Jul 2026 23:59:59 UTC
                "sha256/f78NVAesYtdZ9OGSbK7VtGQkSIVykh3DnduuLIJHMu4=",
                // Backup pin (opsiyonel - daha fazla güvenlik için)
                // Amazon Root CA veya intermediate CA pin'i eklenebilir
                // Şu an için tek pin yeterli (sertifika 2026'ya kadar geçerli)
            )
        } else {
            // Debug build'de pin'leme yok (geliştirme kolaylığı için)
            emptyList()
        }

    /**
     * IPTV API'ler için certificate pin'leri.
     *
     * Not: IPTV sunucuları kullanıcı DNS'ine göre değiştiği için,
     * bu pin'ler opsiyoneldir. Sadece belirli bir IPTV sunucusu için
     * pin'leme yapmak istiyorsanız buraya ekleyin.
     *
     * Örnek:
     * ```kotlin
     * "sha256/XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX=" to listOf("*.example.com", "example.com")
     * ```
     */
    private val IPTV_PINS: Map<String, List<String>> =
        if (BuildConfig.IS_PRODUCTION) {
            // IPTV sunucuları için pin'ler (opsiyonel)
            // Örnek: mapOf("sha256/...=" to listOf("*.iptvserver.com", "iptvserver.com"))
            emptyMap()
        } else {
            emptyMap()
        }

    /**
     * Certificate pin'lerini döndürür.
     *
     * @return Map<host, List<pin>> formatında pin'ler
     */
    fun getCertificatePins(): Map<String, List<String>> {
        val pins = mutableMapOf<String, List<String>>()

        // TMDB API pin'leri
        if (TMDB_PINS.isNotEmpty()) {
            pins["api.themoviedb.org"] = TMDB_PINS
        }

        // IPTV API pin'leri (opsiyonel)
        pins.putAll(IPTV_PINS)

        return pins
    }

    /**
     * Certificate pinning aktif mi?
     */
    fun isPinningEnabled(): Boolean {
        return BuildConfig.IS_PRODUCTION && getCertificatePins().isNotEmpty()
    }
}
