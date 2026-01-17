package com.pnr.tv.repository

import com.pnr.tv.core.constants.NetworkConstants
import kotlin.math.min
import kotlin.random.Random

/**
 * Network policy ve retry stratejileri için yardımcı sınıf.
 * Exponential backoff ve jitter hesaplamalarını yönetir.
 */
object NetworkPolicyHelper {
    /**
     * Exponential backoff delay hesaplar.
     * Her retry'de delay süresi artar: baseDelay * 2^attempt
     * Jitter (rastgele gecikme) eklenerek network congestion önlenir.
     *
     * @param attempt Mevcut retry denemesi (0'dan başlar)
     * @param baseDelayMs Temel delay süresi (milisaniye)
     * @param maxDelayMs Maksimum delay süresi (milisaniye)
     * @param maxJitterMs Maksimum jitter değeri (milisaniye)
     * @return Hesaplanan delay süresi (milisaniye)
     */
    fun calculateExponentialBackoffDelay(
        attempt: Int,
        baseDelayMs: Long,
        maxDelayMs: Long = NetworkConstants.Network.MAX_BACKOFF_DELAY_MILLIS,
        maxJitterMs: Long = NetworkConstants.Network.MAX_JITTER_MILLIS,
    ): Long {
        // Exponential backoff: baseDelay * 2^attempt
        val exponentialDelay = baseDelayMs * (1 shl attempt)

        // Jitter ekle (rastgele gecikme, 0 ile maxJitterMs arası)
        val jitter = Random.nextLong(0, maxJitterMs + 1)

        // Maksimum delay'i aşmamak için min kullan
        return min(exponentialDelay + jitter, maxDelayMs)
    }
}
