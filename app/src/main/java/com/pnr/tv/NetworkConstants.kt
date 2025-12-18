package com.pnr.tv

/**
 * Network ve API ile ilgili sabitler.
 */
object NetworkConstants {
    /**
     * Network ve API ile ilgili sabitler.
     */
    object Network {
        /**
         * HTTP timeout süresi (saniye).
         * IPTV stream'leri için yüksek tutulmuştur.
         */
        const val TIMEOUT_SECONDS = 30

        /**
         * Retry count - Başarısız istekler için tekrar deneme sayısı.
         */
        const val MAX_RETRY_COUNT = 3

        /**
         * Retry delay - Başarısız istek sonrası bekleme süresi (milisaniye).
         */
        const val RETRY_DELAY_MILLIS = 1000L

        /**
         * Image loading için maksimum eşzamanlı istek sayısı.
         * Düşük değer, sunucunun istekleri reddetmesini önler.
         */
        const val MAX_IMAGE_REQUESTS = 1

        /**
         * Image loading için host başına maksimum eşzamanlı istek sayısı.
         */
        const val MAX_IMAGE_REQUESTS_PER_HOST = 1
    }

    /**
     * TMDB (The Movie Database) API ile ilgili sabitler.
     */
    object Tmdb {
        /**
         * TMDB API base URL.
         */
        const val BASE_URL = "https://api.themoviedb.org/3/"

        /**
         * TMDB API key.
         * BuildConfig'den alınır (local.properties'ten okunur).
         * Güvenlik için kod içinde hardcoded değildir.
         */
        val API_KEY: String = BuildConfig.TMDB_API_KEY
    }
}
