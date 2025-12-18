package com.pnr.tv

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
}
