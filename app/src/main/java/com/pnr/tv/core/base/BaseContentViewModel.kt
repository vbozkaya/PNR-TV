package com.pnr.tv.core.base

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.model.SortOrder
import com.pnr.tv.premium.PremiumManager
import com.pnr.tv.repository.FavoriteRepository
import com.pnr.tv.repository.Result
import com.pnr.tv.repository.ViewerRepository
import com.pnr.tv.util.validation.AdultContentPreferenceManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Content ViewModel'ler için ortak helper metodları içeren base sınıf.
 *
 * MovieViewModel ve SeriesViewModel gibi content ViewModel'lerinde
 * ortak olan helper metodları burada toplar.
 */
abstract class BaseContentViewModel : BaseViewModel() {
    /**
     * Kategori refresh metodunu çağıran abstract metod.
     * Child class'lar bu metodu implement ederek kendi repository metodlarını çağırmalı.
     *
     * @return Result<Unit> - Kategori refresh sonucu
     */
    protected abstract suspend fun refreshCategories(): Result<Unit>

    /**
     * Log tag'i için abstract property.
     * Child class'lar kendi log tag'lerini sağlamalı (örn: "🎬", "📺").
     */
    protected abstract val logTag: String

    /**
     * FavoriteRepository'ye erişim için abstract property.
     * Child class'lar bu property'yi implement etmelidir.
     * Not: buildCategories metodunda kullanılıyor.
     */
    protected abstract val favoriteRepository: FavoriteRepository

    /**
     * ContentFavoriteHandler'a erişim için abstract property.
     * Child class'lar bu property'yi implement etmelidir.
     */
    protected abstract val contentFavoriteHandler: ContentFavoriteHandler

    /**
     * Loading state için abstract property.
     * Child class'lar kendi loading StateFlow'larını sağlamalı.
     */
    protected abstract val isLoading: MutableStateFlow<Boolean>

    // Error message artık BaseViewModel'den geliyor (_errorMessage)
    // Child class'lar artık kendi errorMessage'larını sağlamak zorunda değil.

    /**
     * Veritabanında içerik verisi olup olmadığını kontrol eden abstract metod.
     * Child class'lar kendi repository metodlarını çağırmalı.
     */
    protected abstract suspend fun hasData(): Boolean

    /**
     * İçerikleri refresh eden abstract metod.
     * Child class'lar kendi repository metodlarını çağırmalı.
     */
    protected abstract suspend fun refreshContent(): Result<Unit>

    /**
     * İçerik yükleme hatası için string resource ID.
     * Child class'lar kendi error mesaj ID'lerini sağlamalı.
     */
    protected abstract val contentLoadErrorStringId: Int

    /**
     * Error context için string (loglama için).
     * Child class'lar kendi context string'lerini sağlamalı (örn: "MovieViewModel").
     */
    protected abstract val errorContext: String

    /**
     * ViewerRepository'ye erişim için abstract property.
     * Child class'lar bu property'yi implement etmelidir.
     */
    protected abstract val viewerRepository: ViewerRepository

    /**
     * AdultContentPreferenceManager'a erişim için abstract property.
     * Child class'lar bu property'yi implement etmelidir.
     */
    protected abstract val adultContentPreferenceManager: AdultContentPreferenceManager

    /**
     * PremiumManager'a erişim için abstract property.
     * Child class'lar bu property'yi implement etmelidir.
     */
    protected abstract val premiumManager: com.pnr.tv.premium.PremiumManager

    /**
     * CategoryBuilder'a erişim için abstract property.
     * Child class'lar bu property'yi implement etmelidir.
     */
    protected abstract val categoryBuilder: CategoryBuilder

    /**
     * Normal kategorileri getiren abstract metod.
     * Child class'lar kendi repository metodlarını çağırmalı.
     */
    protected abstract fun getNormalCategories(): Flow<List<CategoryItem>>

    /**
     * Tüm içerikleri getiren abstract metod (filtrelenmemiş).
     * Child class'lar kendi repository metodlarını çağırmalı.
     */
    protected abstract fun getAllContent(): Flow<List<ContentItem>>

    /**
     * Kategori ID'ye göre içerik sayılarını döndüren abstract metod (performans optimizasyonu için).
     * Child class'lar kendi repository metodlarını çağırmalı.
     */
    protected abstract suspend fun getCategoryCounts(): Map<String, Int>

    /**
     * İçeriğin yetişkin içerik olup olmadığını kontrol eden abstract metod.
     * Child class'lar kendi entity tiplerine göre kontrol yapmalı.
     */
    protected abstract fun ContentItem.isAdultContent(): Boolean

    /**
     * İçeriğin kategori ID'sini döndüren abstract metod.
     * Child class'lar kendi entity tiplerine göre kategori ID'yi döndürmeli.
     */
    protected abstract fun ContentItem.getCategoryId(): String?

    /**
     * Sanal kategori entity'si oluşturan abstract metod.
     * Child class'lar kendi entity tiplerini oluşturmalı.
     */
    protected abstract fun createVirtualCategoryEntity(
        categoryId: String,
        categoryName: String,
        parentId: Int,
        sortOrder: Int,
    ): CategoryItem

    /**
     * Virtual category ID'leri için abstract property'ler.
     */
    protected abstract val virtualCategoryIdFavorites: String
    protected abstract val virtualCategoryIdRecentlyAdded: String

    /**
     * Sadece kategorileri günceller (içerikleri yüklemez).
     * Fragment onResume() içinde çağrılır, kaynak değişikliklerini yansıtmak için.
     */
    fun refreshCategoriesOnly() {
        viewModelScope.launch {
            try {
                val categoriesResult = refreshCategories()
                if (categoriesResult is Result.Error) {
                    // Kategori güncelleme hatası
                }
            } catch (e: Exception) {
                // Kategori güncelleme sırasında hata
            }
        }
    }

    /**
     * İçerik listesine arama filtresi uygular.
     * ContentFilterDelegate'e yönlendirir.
     *
     * @param items Filtrelenecek içerik listesi
     * @param query Arama sorgusu
     * @return Filtrelenmiş içerik listesi
     */
    protected fun applySearchFilter(
        items: List<ContentItem>,
        query: String,
    ): List<ContentItem> = ContentFilterDelegate.applySearchFilter(items, query)

    /**
     * İçerik listesine yetişkin içerik filtresi uygular.
     * ContentFilterDelegate'e yönlendirir.
     *
     * @param items Filtrelenecek içerik listesi
     * @param adultContentEnabled Yetişkin içerikler açık mı?
     * @return Filtrelenmiş içerik listesi
     */
    protected fun applyAdultContentFilter(
        items: List<ContentItem>,
        adultContentEnabled: Boolean,
    ): List<ContentItem> = ContentFilterDelegate.applyAdultContentFilter(items, adultContentEnabled)

    /**
     * İçerik listesine sıralama uygular.
     * ContentFilterDelegate'e yönlendirir.
     *
     * @param items Sıralanacak içerik listesi
     * @param sortOrder Sıralama tipi
     * @return Sıralanmış içerik listesi
     */
    protected fun applySorting(
        items: List<ContentItem>,
        sortOrder: SortOrder?,
    ): List<ContentItem> = ContentFilterDelegate.applySorting(items, sortOrder)

    /**
     * İçeriği favorilere ekler.
     * ContentFavoriteHandler üzerinden işlemi yürütür.
     *
     * @param contentId İçerik ID'si
     * @param viewerId Viewer ID'si
     */
    protected fun addFavorite(
        contentId: Int,
        viewerId: Int,
    ) {
        contentFavoriteHandler.addFavorite(
            scope = viewModelScope,
            contentId = contentId,
            viewerId = viewerId,
            onToastMessage = { message -> showToast(message) },
        )
    }

    /**
     * İçeriği favorilerden çıkarır.
     * ContentFavoriteHandler üzerinden işlemi yürütür.
     *
     * @param contentId İçerik ID'si
     * @param viewerId Viewer ID'si
     */
    protected fun removeFavorite(
        contentId: Int,
        viewerId: Int,
    ) {
        contentFavoriteHandler.removeFavorite(
            scope = viewModelScope,
            contentId = contentId,
            viewerId = viewerId,
            onToastMessage = { message -> showToast(message) },
        )
    }

    /**
     * İçeriğin favori olup olmadığını kontrol eder.
     * ContentFavoriteHandler üzerinden işlemi yürütür.
     *
     * @param contentId İçerik ID'si
     * @param viewerId Viewer ID'si
     * @return Flow<Boolean> - Favori durumu
     */
    protected fun isFavorite(
        contentId: Int,
        viewerId: Int,
    ): Flow<Boolean> = contentFavoriteHandler.isFavorite(contentId, viewerId)

    /**
     * İçerikleri yükler (eğer daha önce yüklenmemişse).
     * İçerikler sadece yoksa yüklenir (performans için).
     * Kategoriler sadece ana sayfadan "Güncelle" butonuna basıldığında güncellenir.
     *
     * CACHE KONTROLÜ: Eğer veriler zaten yüklüyse, loading state'i tetiklemez ve
     * Flow'ları gereksiz yere yeniden yüklemez. Veriler veritabanından okunur.
     */
    protected fun loadContentIfNeeded() {
        viewModelScope.launch {
            clearError()

            // Önce veritabanında içerik verisi olup olmadığını kontrol et
            val hasData = hasData()

            // Eğer veriler zaten yüklüyse, hiçbir şey yapma
            // Veriler veritabanından Flow'lar aracılığıyla otomatik olarak gösterilir
            if (hasData) {
                // Loading state'i tetikleme - veriler zaten yüklü
                return@launch
            }

            // Veriler yoksa, normal yükleme işlemini yap
            isLoading.value = true

            try {
                // Kategorileri önce güncelle (içerikler kategori ID'lerine bağlı olduğu için)
                val categoriesResult = refreshCategories()
                if (categoriesResult is Result.Error) {
                    // ErrorHelper'dan gelen kullanıcı dostu mesajı kullan
                    setError(categoriesResult)
                    isLoading.value = false
                    return@launch
                }

                // İçerikler yoksa yükle
                val contentResult = refreshContent()
                if (contentResult is Result.Error) {
                    // ErrorHelper'dan gelen kullanıcı dostu mesajı kullan
                    setError(contentResult)
                } else if (contentResult is Result.PartialSuccess) {
                    // Kısmi başarı - veriyi kullan ama uyarı göster
                    contentResult.errorMessage?.let {
                        Timber.w("Kısmi başarı: $it")
                    }
                }
            } catch (e: Exception) {
                // ErrorHelper kullanarak kullanıcı dostu mesaj oluştur
                handleError(e, errorContext)
            } finally {
                isLoading.value = false
            }
        }
    }

    /**
     * Kategorileri oluşturan generic helper metod.
     * CategoryBuilder'a yönlendirir.
     *
     * @return Flow<List<CategoryItem>> - Oluşturulan kategoriler
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    protected fun buildCategories(): Flow<List<CategoryItem>> {
        return categoryBuilder.buildCategories(
            object : CategoryBuilderCallback {
                override fun getNormalCategories(): Flow<List<CategoryItem>> = this@BaseContentViewModel.getNormalCategories()

                override fun getAllContent(): Flow<List<ContentItem>> = this@BaseContentViewModel.getAllContent()

                override suspend fun getCategoryCounts(): Map<String, Int> = this@BaseContentViewModel.getCategoryCounts()

                override fun isAdultContent(contentItem: ContentItem): Boolean =
                    with(this@BaseContentViewModel) { contentItem.isAdultContent() }

                override fun getCategoryId(contentItem: ContentItem): String? =
                    with(this@BaseContentViewModel) { contentItem.getCategoryId() }

                override fun createVirtualCategoryEntity(
                    categoryId: String,
                    categoryName: String,
                    parentId: Int,
                    sortOrder: Int,
                ): CategoryItem =
                    this@BaseContentViewModel.createVirtualCategoryEntity(
                        categoryId,
                        categoryName,
                        parentId,
                        sortOrder,
                    )

                override val virtualCategoryIdFavorites: String
                    get() = this@BaseContentViewModel.virtualCategoryIdFavorites

                override val virtualCategoryIdRecentlyAdded: String
                    get() = this@BaseContentViewModel.virtualCategoryIdRecentlyAdded

                override val context: Context
                    get() = this@BaseContentViewModel.context
            },
        )
    }
}
