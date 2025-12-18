package com.pnr.tv

/**
 * İçerik ile ilgili sabitler.
 * Sanal kategori ID'leri ve sıralama değerleri.
 */
object ContentConstants {
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
}
