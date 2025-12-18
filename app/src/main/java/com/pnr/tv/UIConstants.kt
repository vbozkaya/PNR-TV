package com.pnr.tv

/**
 * UI ile ilgili sabitler.
 * Grid layout, kart boyutları, görsel ayarları ve layout sabitleri.
 */
object UIConstants {
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
}
