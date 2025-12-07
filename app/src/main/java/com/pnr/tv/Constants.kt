package com.pnr.tv

/**
 * Uygulama genelinde kullanılan sabitler.
 * Magic number'ları ve hardcoded değerleri burada toplar.
 */
object Constants {
    // ==================== UI Constants ====================

    /**
     * Grid layout için sütun sayısı.
     * Filmler, diziler ve canlı yayınlar için kullanılır.
     */
    const val GRID_COLUMN_COUNT = 5

    /**
     * Card genişliği hesaplama için bölen.
     * Ekran genişliği bu değere bölünerek kart genişliği hesaplanır.
     */
    const val CARD_WIDTH_DIVISOR = 7.5f

    /**
     * ImageView corner radius değeri (dp).
     * Film ve dizi posterleri için kullanılır.
     */
    const val CORNER_RADIUS_DP = 7f

    // ==================== Virtual Category IDs ====================

    /**
     * String tipinde sanal kategori ID'leri (Filmler ve Diziler için).
     */
    object VirtualCategoryIds {
        /**
         * Favoriler kategorisi ID'si.
         */
        const val FAVORITES_STRING = "-1"

        /**
         * Son Eklenenler kategorisi ID'si.
         */
        const val RECENTLY_ADDED_STRING = "-2"

        /**
         * Tüm içerikler kategorisi ID'si.
         */
        const val ALL_STRING = "0"
    }

    /**
     * Int tipinde sanal kategori ID'leri (Canlı Yayınlar için).
     */
    object VirtualCategoryIdsInt {
        /**
         * Favoriler kategorisi ID'si.
         */
        const val FAVORITES = -1

        /**
         * Son İzlenenler kategorisi ID'si.
         */
        const val RECENTLY_WATCHED = -2
    }

    // ==================== Sort Order Constants ====================

    /**
     * Kategori sıralama değerleri.
     */
    object SortOrder {
        /**
         * Favoriler kategorisi için sıralama değeri (en üstte).
         */
        const val FAVORITES = -2

        /**
         * Tüm içerikler kategorisi için sıralama değeri.
         */
        const val ALL = -1

        /**
         * Varsayılan sıralama değeri.
         */
        const val DEFAULT = 0
    }

    // ==================== Time Constants ====================

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
     * UI etkileşimleri için delay değerleri (milisaniye).
     */
    object DelayDurations {
        /**
         * Güncelleme tamamlandıktan sonra gösterim süresi.
         */
        const val UPDATE_COMPLETED_DELAY = 2000L

        /**
         * Klavye gösterme için delay.
         * Klavyenin focus değişiminden sonra düzgün açılması için bekleme süresi.
         */
        const val KEYBOARD_SHOW_DELAY = 100L

        /**
         * Player pozisyon güncelleme aralığı.
         * Oynatıcı progress bar'ının ne sıklıkla güncelleneceği.
         */
        const val PLAYER_POSITION_UPDATE_INTERVAL = 1000L
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

    // ==================== Database Constants ====================

    /**
     * Son İzlenenler için varsayılan limit değeri.
     */
    const val RECENTLY_WATCHED_DEFAULT_LIMIT = 50

    // ==================== Image/Card Constants ====================

    /**
     * Kart görüntü boyutları.
     */
    object CardDimensions {
        /**
         * Standart TV kartı genişliği (16:9 aspect ratio).
         */
        const val CARD_WIDTH_16_9 = 313

        /**
         * Standart TV kartı yüksekliği (16:9 aspect ratio).
         */
        const val CARD_HEIGHT_16_9 = 176

        /**
         * Film posteri genişliği (2:3 aspect ratio).
         */
        const val POSTER_WIDTH_2_3 = 200

        /**
         * Film posteri yüksekliği (2:3 aspect ratio).
         */
        const val POSTER_HEIGHT_2_3 = 300
    }

    // ==================== Network Constants ====================

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

    // ==================== TMDB API Constants ====================

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

    // ==================== Player Constants ====================

    /**
     * Video player ile ilgili sabitler.
     */
    object Player {
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
    }

    // ==================== Grid/List Constants ====================

    /**
     * Liste ve grid görünümleri için sabitler.
     */
    object Layout {
        /**
         * Kullanıcı listesi için grid sütun sayısı.
         */
        const val USER_GRID_COLUMNS = 3

        /**
         * Kategori listesi için minimum item sayısı.
         */
        const val MIN_CATEGORY_ITEMS = 1

        /**
         * Maksimum cache boyutu (item sayısı).
         */
        const val MAX_CACHE_SIZE = 100
    }
}
