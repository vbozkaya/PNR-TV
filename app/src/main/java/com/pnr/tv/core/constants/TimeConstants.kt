package com.pnr.tv.core.constants

/**
 * Zaman ile ilgili sabitler.
 */
object TimeConstants {
    /**
     * İzleme süresi ile ilgili sabitler.
     */
    object WatchingDuration {
        /**
         * Son İzlenenler'e eklemek için minimum izleme süresi (dakika).
         */
        const val MINUTES = 5

        /**
         * Son İzlenenler'e eklemek için minimum izleme süresi (milisaniye).
         * 5 dakika = 5 * 60 * 1000 = 300000 milisaniye
         */
        const val MILLIS = 5 * 60 * 1000L
    }

    /**
     * Unix timestamp'i milisaniyeye çevirmek için çarpan.
     * Unix timestamp saniye cinsinden, Android Date milisaniye cinsinden.
     */
    const val TIMESTAMP_TO_MILLIS_MULTIPLIER = 1000L

    /**
     * Saniye cinsinden değeri milisaniyeye çevirme.
     */
    const val SECONDS_TO_MILLIS = 1000L

    /**
     * Dakika cinsinden değeri milisaniyeye çevirme.
     */
    const val MINUTES_TO_MILLIS = 60 * 1000L

    /**
     * Saat cinsinden değeri milisaniyeye çevirme.
     */
    const val HOURS_TO_MILLIS = 60 * MINUTES_TO_MILLIS

    /**
     * Gün cinsinden değeri milisaniyeye çevirme.
     */
    const val DAYS_TO_MILLIS = 24 * HOURS_TO_MILLIS

    /**
     * Sık kullanılan zaman aralıkları (milisaniye).
     */
    object Intervals {
        /**
         * 10 dakika (milisaniye).
         * Watch progress threshold için kullanılır.
         */
        const val TEN_MINUTES_MS = 10 * MINUTES_TO_MILLIS

        /**
         * 30 saniye (milisaniye).
         * Player position save interval için kullanılır.
         */
        const val THIRTY_SECONDS_MS = 30 * SECONDS_TO_MILLIS

        /**
         * 30 gün (milisaniye).
         * Playback position cleanup için kullanılır.
         */
        const val THIRTY_DAYS_MS = 30 * DAYS_TO_MILLIS

        /**
         * 24 saat (milisaniye).
         * Cache validity duration için kullanılır.
         */
        const val TWENTY_FOUR_HOURS_MS = 24 * HOURS_TO_MILLIS
    }
}
