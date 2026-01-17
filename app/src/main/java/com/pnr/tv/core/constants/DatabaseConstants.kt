package com.pnr.tv.core.constants

/**
 * Veritabanı ile ilgili sabitler.
 */
object DatabaseConstants {
    /**
     * Son İzlenenler için varsayılan limit değeri.
     */
    const val RECENTLY_WATCHED_DEFAULT_LIMIT = 50

    /**
     * Son Eklenenler için limit değeri.
     * Recently added movies/series için kullanılır.
     */
    const val RECENTLY_ADDED_LIMIT = 20

    /**
     * Liste boyutu eşikleri.
     */
    object ListSizeThresholds {
        /**
         * Büyük liste eşiği.
         * Bu değerden büyük listeler "büyük" olarak kabul edilir.
         */
        const val LARGE_LIST_THRESHOLD = 1000

        /**
         * Küçük liste eşiği.
         * Bu değerden küçük listeler "küçük" olarak kabul edilir.
         */
        const val SMALL_LIST_THRESHOLD = 500
    }

    /**
     * UI cache boyutları.
     */
    object CacheSizes {
        /**
         * RecyclerView item view cache size.
         * Önceden oluşturulmuş view'ları cache'lemek için.
         */
        const val RECYCLER_VIEW_ITEM_CACHE_SIZE = 20
    }

    /**
     * Progress ve watch progress değerleri.
     */
    object Progress {
        /**
         * Tam izlenme yüzdesi (100%).
         */
        const val FULL_PROGRESS = 100

        /**
         * Varsayılan watch progress değeri.
         */
        const val DEFAULT_WATCH_PROGRESS = 100
    }
}
