package com.pnr.tv.core.base

import com.pnr.tv.db.entity.MovieEntity
import com.pnr.tv.db.entity.SeriesEntity
import com.pnr.tv.model.ContentItem
import com.pnr.tv.model.SortOrder

/**
 * İçerik filtreleme ve sıralama işlemlerini yöneten delegate sınıfı.
 * BaseContentViewModel'den filtreleme ve sıralama mantığını ayırmak için oluşturulmuştur.
 */
object ContentFilterDelegate {
    /**
     * İçerik listesine arama filtresi uygular.
     *
     * @param items Filtrelenecek içerik listesi
     * @param query Arama sorgusu
     * @return Filtrelenmiş içerik listesi
     */
    fun applySearchFilter(
        items: List<ContentItem>,
        query: String,
    ): List<ContentItem> {
        if (query.length < 3) {
            return items
        }

        val lowerQuery = query.lowercase()
        return items.filter { item ->
            item.title.lowercase().contains(lowerQuery)
        }
    }

    /**
     * İçerik listesine yetişkin içerik filtresi uygular.
     *
     * @param items Filtrelenecek içerik listesi
     * @param adultContentEnabled Yetişkin içerikler açık mı?
     * @return Filtrelenmiş içerik listesi
     */
    fun applyAdultContentFilter(
        items: List<ContentItem>,
        adultContentEnabled: Boolean,
    ): List<ContentItem> {
        if (adultContentEnabled) {
            // Yetişkin içerikler açıksa tüm içerikleri göster
            return items
        }

        // Yetişkin içerikler kapalıysa yetişkin içerikleri filtrele
        return items.filter { item ->
            when (item) {
                is MovieEntity -> {
                    // isAdult true ise filtrele (gösterme), null veya false ise göster
                    item.isAdult != true
                }
                is SeriesEntity -> {
                    // isAdult true ise filtrele (gösterme), null veya false ise göster
                    item.isAdult != true
                }
                else -> true // Diğer içerik türleri için kontrol yok (şimdilik)
            }
        }
    }

    /**
     * İçerik listesine sıralama uygular.
     *
     * @param items Sıralanacak içerik listesi
     * @param sortOrder Sıralama tipi
     * @return Sıralanmış içerik listesi
     */
    fun applySorting(
        items: List<ContentItem>,
        sortOrder: SortOrder?,
    ): List<ContentItem> {
        if (sortOrder == null) return items

        return when (sortOrder) {
            SortOrder.A_TO_Z -> items.sortedBy { it.title.lowercase() }
            SortOrder.Z_TO_A -> items.sortedByDescending { it.title.lowercase() }
            SortOrder.RATING_HIGH_TO_LOW ->
                items.sortedByDescending { item ->
                    when (item) {
                        is MovieEntity -> item.rating ?: 0.0
                        is SeriesEntity -> item.rating ?: 0.0
                        else -> 0.0
                    }
                }
            SortOrder.RATING_LOW_TO_HIGH ->
                items.sortedBy { item ->
                    when (item) {
                        is MovieEntity -> item.rating ?: Double.MAX_VALUE
                        is SeriesEntity -> item.rating ?: Double.MAX_VALUE
                        else -> Double.MAX_VALUE
                    }
                }
            SortOrder.DATE_NEW_TO_OLD ->
                items.sortedByDescending { item ->
                    when (item) {
                        is MovieEntity -> item.added
                        is SeriesEntity -> item.added
                        else -> null
                    }?.toLongOrNull() ?: 0L
                }
            SortOrder.DATE_OLD_TO_NEW ->
                items.sortedBy { item ->
                    when (item) {
                        is MovieEntity -> item.added
                        is SeriesEntity -> item.added
                        else -> null
                    }?.toLongOrNull() ?: Long.MAX_VALUE
                }
        }
    }
}
