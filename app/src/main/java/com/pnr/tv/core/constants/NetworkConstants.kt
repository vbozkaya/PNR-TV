package com.pnr.tv.core.constants

import com.pnr.tv.BuildConfig

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
         * Retry delay - Bazı repository'lerde kullanılan uzun retry delay (milisaniye).
         */
        const val LONG_RETRY_DELAY_MILLIS = 2000L

        /**
         * Exponential backoff için maksimum delay süresi (milisaniye).
         * Exponential backoff hesaplamasında bu değeri aşmayacak.
         */
        const val MAX_BACKOFF_DELAY_MILLIS = 30000L // 30 saniye

        /**
         * Exponential backoff için jitter (rastgele gecikme) maksimum değeri (milisaniye).
         * Network congestion'i önlemek için rastgele gecikme eklenir.
         */
        const val MAX_JITTER_MILLIS = 1000L

        /**
         * Rate limiter minimum delay (milisaniye).
         * Ardışık istekler arasındaki minimum gecikme.
         */
        const val RATE_LIMITER_MIN_DELAY_MS = 500L

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

    /**
     * TMDB API ile ilgili sabitler.
     */
    object TmdbApi {
        /**
         * Batch işlemi sırasında istekler arası bekleme süresi (milisaniye).
         * TMDB API rate limiting için (250ms = saniyede 4 istek).
         */
        const val BATCH_REQUEST_DELAY_MS = 250L

        /**
         * TMDB görsel base URL.
         */
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"

        /**
         * Poster görseli için varsayılan boyut (w500 = 500px genişlik).
         */
        const val POSTER_SIZE = "w500"

        /**
         * Backdrop görseli için varsayılan boyut (w780 = 780px genişlik).
         */
        const val BACKDROP_SIZE = "w780"

        /**
         * Yüksek kaliteli poster için boyut (w780 = 780px genişlik).
         */
        const val POSTER_SIZE_LARGE = "w780"

        /**
         * Orijinal boyut (full resolution).
         */
        const val ORIGINAL_SIZE = "original"
    }
}

/**
 * TMDB görsel URL'lerini oluşturan extension fonksiyonlar.
 */
object TmdbImageHelper {
    /**
     * TMDB poster_path'den tam URL oluşturur.
     *
     * @param posterPath TMDB'den gelen poster_path (örn: "/abc123.jpg")
     * @param size Görsel boyutu (varsayılan: w500)
     * @return Tam URL veya null
     */
    fun getPosterUrl(
        posterPath: String?,
        size: String = NetworkConstants.TmdbApi.POSTER_SIZE,
    ): String? {
        if (posterPath.isNullOrBlank()) return null
        // posterPath zaten "/" ile başlıyor, ekstra "/" eklemeye gerek yok
        return "${NetworkConstants.TmdbApi.IMAGE_BASE_URL}$size$posterPath"
    }

    /**
     * TMDB backdrop_path'den tam URL oluşturur.
     *
     * @param backdropPath TMDB'den gelen backdrop_path (örn: "/abc123.jpg")
     * @param size Görsel boyutu (varsayılan: w780)
     * @return Tam URL veya null
     */
    fun getBackdropUrl(
        backdropPath: String?,
        size: String = NetworkConstants.TmdbApi.BACKDROP_SIZE,
    ): String? {
        if (backdropPath.isNullOrBlank()) return null
        // backdropPath zaten "/" ile başlıyor, ekstra "/" eklemeye gerek yok
        return "${NetworkConstants.TmdbApi.IMAGE_BASE_URL}$size$backdropPath"
    }
}
