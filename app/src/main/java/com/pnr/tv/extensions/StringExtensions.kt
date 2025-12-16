package com.pnr.tv.extensions

/**
 * String için extension functions.
 * DNS URL normalizasyonu ve diğer string işlemleri için kullanılır.
 */

/**
 * DNS URL'yi normalizasyon eder.
 * - Eğer zaten http:// veya https:// ile başlıyorsa, trailing slash ekler
 * - Eğer başlamıyorsa, http:// ekler ve trailing slash ekler
 *
 * @return Normalize edilmiş DNS URL (trailing slash ile)
 *
 * Örnek:
 * - "example.com" -> "http://example.com/"
 * - "example.com/" -> "http://example.com/"
 * - "http://example.com" -> "http://example.com/"
 * - "https://example.com/" -> "https://example.com/"
 */
fun String.normalizeDnsUrl(): String {
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return trimmed

    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
            if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
        else -> {
            if (trimmed.endsWith("/")) "http://$trimmed" else "http://$trimmed/"
        }
    }
}

/**
 * Base URL'yi normalizasyon eder (trailing slash olmadan).
 * Stream URL oluştururken kullanılır.
 *
 * @return Normalize edilmiş base URL (trailing slash olmadan)
 *
 * Örnek:
 * - "example.com" -> "http://example.com"
 * - "example.com/" -> "http://example.com"
 * - "http://example.com" -> "http://example.com"
 * - "https://example.com/" -> "https://example.com"
 */
fun String.normalizeBaseUrl(): String {
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return trimmed

    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
            if (trimmed.endsWith("/")) trimmed.dropLast(1) else trimmed
        }
        else -> {
            if (trimmed.endsWith("/")) "http://${trimmed.dropLast(1)}" else "http://$trimmed"
        }
    }
}
