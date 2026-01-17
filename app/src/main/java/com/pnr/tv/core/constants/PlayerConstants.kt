package com.pnr.tv.core.constants

/**
 * Video player ile ilgili sabitler.
 */
object PlayerConstants {
    /**
     * Varsayılan buffering süresi (milisaniye).
     */
    const val DEFAULT_BUFFER_DURATION = 15000L

    /**
     * Minimum buffering süresi (milisaniye).
     */
    const val MIN_BUFFER_DURATION = 5000L

    /**
     * Maximum buffering süresi (milisaniye).
     */
    const val MAX_BUFFER_DURATION = 50000L

    /**
     * Seek backward/forward süresi (milisaniye).
     */
    const val SEEK_INCREMENT_MS = 10000L

    /**
     * 30 saniye seek increment (milisaniye).
     * PlayerControlView'da kullanılan varsayılan seek miktarı.
     */
    const val SEEK_INCREMENT_30_SECONDS_MS = 30 * 1000L
}
