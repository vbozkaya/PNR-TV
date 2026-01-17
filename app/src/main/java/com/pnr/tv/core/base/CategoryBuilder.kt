package com.pnr.tv.core.base

import android.content.Context
import com.pnr.tv.core.constants.ContentConstants
import com.pnr.tv.R
import com.pnr.tv.db.entity.MovieEntity
import com.pnr.tv.db.entity.SeriesEntity
import com.pnr.tv.db.entity.ViewerEntity
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.premium.PremiumManager
import com.pnr.tv.repository.FavoriteRepository
import com.pnr.tv.repository.ViewerRepository
import com.pnr.tv.util.validation.AdultContentPreferenceManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/**
 * Kategori oluşturma için gerekli callback interface'i.
 * BaseContentViewModel'den abstract metodları bu interface üzerinden alır.
 */
interface CategoryBuilderCallback {
    /**
     * Normal kategorileri getirir.
     */
    fun getNormalCategories(): Flow<List<CategoryItem>>

    /**
     * Tüm içerikleri getirir (filtrelenmemiş).
     */
    fun getAllContent(): Flow<List<ContentItem>>

    /**
     * Kategori ID'ye göre içerik sayılarını döndürür (performans optimizasyonu için).
     */
    suspend fun getCategoryCounts(): Map<String, Int>

    /**
     * İçeriğin yetişkin içerik olup olmadığını kontrol eder.
     */
    fun isAdultContent(contentItem: ContentItem): Boolean

    /**
     * İçeriğin kategori ID'sini döndürür.
     */
    fun getCategoryId(contentItem: ContentItem): String?

    /**
     * Sanal kategori entity'si oluşturur.
     */
    fun createVirtualCategoryEntity(
        categoryId: String,
        categoryName: String,
        parentId: Int,
        sortOrder: Int,
    ): CategoryItem

    /**
     * Virtual category ID'leri.
     */
    val virtualCategoryIdFavorites: String
    val virtualCategoryIdRecentlyAdded: String

    /**
     * Context (string resource'lar için).
     */
    val context: Context
}

/**
 * Kategori oluşturma işlemlerini yöneten builder sınıfı.
 * BaseContentViewModel'den kategori oluşturma mantığını ayırmak için oluşturulmuştur.
 */
class CategoryBuilder
    @Inject
    constructor(
        private val viewerRepository: ViewerRepository,
        private val favoriteRepository: FavoriteRepository,
        private val adultContentPreferenceManager: AdultContentPreferenceManager,
        private val premiumManager: PremiumManager,
    ) {
        /**
         * Kategorileri oluşturan generic helper metod.
         * Tüm content ViewModel'lerinde ortak olan kategori oluşturma mantığını içerir.
         *
         * @param callback CategoryBuilderCallback - Abstract metodlar için callback
         * @return Flow<List<CategoryItem>> - Oluşturulan kategoriler
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun buildCategories(callback: CategoryBuilderCallback): Flow<List<CategoryItem>> {
            fun getViewerCategoryId(viewerId: Int): String = "viewer_$viewerId"

            return combine(
                combine(
                    callback.getNormalCategories(),
                    viewerRepository.getViewerIdsWithFavorites(),
                    viewerRepository.getAllViewers(),
                ) { normalCategories: List<CategoryItem>,
                    viewerIdsWithFavorites: List<Int>,
                    allViewers: List<ViewerEntity>,
                    ->
                    Triple(normalCategories, viewerIdsWithFavorites, allViewers)
                },
                // Varsayılan izleyicinin favori ID'lerini al
                viewerRepository.getAllViewers().flatMapLatest { allViewers ->
                    val defaultViewer = allViewers.find { !it.isDeletable }
                    if (defaultViewer != null) {
                        favoriteRepository.getFavoriteChannelIds(defaultViewer.id)
                    } else {
                        kotlinx.coroutines.flow.flowOf(emptyList())
                    }
                },
                adultContentPreferenceManager.isAdultContentEnabled().onStart { emit(false) },
                premiumManager.isPremium().onStart { emit(false) },
                // Performans optimizasyonu: Kategori sayısı için sadece count kullan, tüm içerikleri almak yerine
                // Ancak favoriler kontrolü için hala getAllContent() kullanılıyor
                callback.getAllContent().flatMapLatest { allContent ->
                    // Kategori sayılarını hesaplamak için getCategoryCounts() kullan
                    flow {
                        val counts = callback.getCategoryCounts()
                        emit(Pair(allContent, counts))
                    }
                },
            ) { triple: Triple<List<CategoryItem>, List<Int>, List<ViewerEntity>>,
                defaultViewerFavoriteIds: List<Int>,
                adultContentEnabled: Boolean,
                isPremium: Boolean,
                contentAndCounts: Pair<List<ContentItem>, Map<String, Int>>,
                ->
                val (normalCategories, viewerIdsWithFavorites, allViewers) = triple
                val (allContent, categoryCountsFromDb) = contentAndCounts

                // Yetişkin içerik ayarına göre filtrelenmiş içerikler (favoriler kontrolü için)
                val filteredContent =
                    if (adultContentEnabled) {
                        allContent
                    } else {
                        allContent.filter { content ->
                            !callback.isAdultContent(content)
                        }
                    }

                // Kategori ID'ye göre içerik sayısını hesapla
                // Performans optimizasyonu: Veritabanından gelen count'ları kullan, ancak yetişkin içerik filtresi için manuel hesaplama yap
                // Eğer yetişkin içerik filtresi kapalıysa, veritabanı count'larını kullan
                // Eğer yetişkin içerik filtresi açıksa, tüm içerikleri say (mevcut mantık)
                val categoryContentCounts =
                    if (adultContentEnabled) {
                        // Yetişkin içerik açıksa, veritabanı count'larını kullan (tüm içerikler dahil)
                        categoryCountsFromDb
                    } else {
                        // Yetişkin içerik kapalıysa, filtrelenmiş içeriklerden say
                        filteredContent
                            .mapNotNull { content ->
                                callback.getCategoryId(content)?.let { categoryId ->
                                    categoryId to content
                                }
                            }
                            .groupBy({ it.first }, { it.second })
                            .mapValues { it.value.size }
                    }

                mutableListOf<CategoryItem>().apply {
                    // Son Eklenenler - her zaman göster (içerik sayısına bakmadan)
                    add(
                        callback.createVirtualCategoryEntity(
                            categoryId = callback.virtualCategoryIdRecentlyAdded,
                            categoryName = callback.context.getString(R.string.category_recently_added),
                            parentId = ContentConstants.SortOrder.DEFAULT,
                            sortOrder = ContentConstants.SortOrder.FAVORITES - 1,
                        ),
                    )

                    val defaultViewer = allViewers.find { !it.isDeletable }
                    val defaultViewerHasFavorites = defaultViewer?.let { it.id in viewerIdsWithFavorites } ?: false

                    // Favoriler - sadece premium kullanıcılar için ve bu içerik tipine ait favoriler varsa göster
                    // Örneğin: Filmler sayfasında sadece film favorileri varsa, Diziler sayfasında sadece dizi favorileri varsa
                    // Premium kontrolü: Sadece premium kullanıcılar favori kategorilerini görebilir
                    if (isPremium && defaultViewerHasFavorites && defaultViewer != null && defaultViewerFavoriteIds.isNotEmpty()) {
                        // Varsayılan izleyicinin favori ID'lerinin bu içerik tipine ait olup olmadığını kontrol et
                        // filteredContent içindeki içeriklerin streamId'leri ile karşılaştır
                        val hasFavoritesForThisContentType =
                            defaultViewerFavoriteIds.any { favoriteId ->
                                filteredContent.any { content ->
                                    when (content) {
                                        is MovieEntity -> content.streamId == favoriteId
                                        is SeriesEntity -> content.streamId == favoriteId
                                        else -> false
                                    }
                                }
                            }

                        if (hasFavoritesForThisContentType) {
                            add(
                                callback.createVirtualCategoryEntity(
                                    categoryId = callback.virtualCategoryIdFavorites,
                                    categoryName = callback.context.getString(R.string.category_favorites),
                                    parentId = ContentConstants.SortOrder.DEFAULT,
                                    sortOrder = ContentConstants.SortOrder.FAVORITES,
                                ),
                            )
                        }
                    }

                    // Viewer favorileri - sadece premium kullanıcılar için ve içerik varsa göster
                    if (isPremium) {
                        viewerIdsWithFavorites.forEach { viewerId ->
                            val viewer = allViewers.find { it.id == viewerId }
                            if (viewer != null && viewer.isDeletable) {
                                add(
                                    callback.createVirtualCategoryEntity(
                                        categoryId = getViewerCategoryId(viewerId),
                                        categoryName =
                                            callback.context.getString(
                                                R.string.category_viewer_favorites,
                                                viewer.name.uppercase(),
                                            ),
                                        parentId = ContentConstants.SortOrder.DEFAULT,
                                        sortOrder = ContentConstants.SortOrder.FAVORITES,
                                    ),
                                )
                            }
                        }
                    }

                    // Normal kategoriler - sadece içerik varsa göster
                    normalCategories.forEach { category ->
                        val categoryId = category.categoryId
                        val contentCount = categoryContentCounts[categoryId] ?: 0
                        if (contentCount > 0) {
                            add(category)
                        }
                    }
                }
            }
        }
    }
