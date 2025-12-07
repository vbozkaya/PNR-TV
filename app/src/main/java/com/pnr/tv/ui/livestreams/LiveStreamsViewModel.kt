package com.pnr.tv.ui.livestreams

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.pnr.tv.Constants
import com.pnr.tv.R
import com.pnr.tv.db.entity.LiveStreamCategoryEntity
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.domain.BuildLiveStreamUrlUseCase
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Canlı yayınlar için ViewModel.
 * Kategorileri ve kanalları yönetir.
 */
@HiltViewModel
class LiveStreamsViewModel
    @Inject
    constructor(
        private val contentRepository: ContentRepository,
        private val buildLiveStreamUrlUseCase: BuildLiveStreamUrlUseCase,
        @ApplicationContext override val context: Context,
    ) : BaseViewModel() {
        companion object {
            // Sanal kategori ID'leri
            val VIRTUAL_CATEGORY_ID_FAVORITES = Constants.VirtualCategoryIdsInt.FAVORITES
            val VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED = Constants.VirtualCategoryIdsInt.RECENTLY_WATCHED
        }

        // For now, use default viewer (id = 1)
        // TODO: Allow user to select viewer for favorites
        private val defaultViewerId = 1
        private val favoriteChannelIds: Flow<List<Int>> = contentRepository.getFavoriteChannelIds(defaultViewerId)
        private val recentlyWatchedChannelIds: Flow<List<Int>> = contentRepository.getRecentlyWatchedChannelIds()

        // Normal kategoriler ile sanal kategorileri birleştir ve CategoryItem'a map et
        val categories: Flow<List<CategoryItem>> =
            combine(
                contentRepository.getLiveStreamCategories(),
                favoriteChannelIds,
                recentlyWatchedChannelIds,
            ) { normalCategories, _, _ ->
                val categoriesWithVirtual = mutableListOf<LiveStreamCategoryEntity>()

                // Favoriler kategorisini her zaman en başa ekle
                categoriesWithVirtual.add(
                    LiveStreamCategoryEntity(
                        categoryIdInt = VIRTUAL_CATEGORY_ID_FAVORITES,
                        categoryName = context.getString(R.string.category_favorites),
                        sortOrder = Constants.SortOrder.FAVORITES,
                    ),
                )

                // Son İzlenenler kategorisini her zaman ekle
                categoriesWithVirtual.add(
                    LiveStreamCategoryEntity(
                        categoryIdInt = VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED,
                        categoryName = context.getString(R.string.category_recently_watched),
                        sortOrder = Constants.SortOrder.ALL,
                    ),
                )

                // Normal kategorileri ekle
                categoriesWithVirtual.addAll(normalCategories)

                // LiveStreamCategoryEntity now implements CategoryItem directly
                categoriesWithVirtual
            }

        // Selected category ID (String format - CategoryAdapter için)
        private val _selectedCategoryId = MutableStateFlow<String?>(null)
        val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

        /**
         * Seçili kategoriye ait kanalları döndürür.
         * selectedCategoryId değiştiğinde otomatik olarak güncellenir.
         * Sanal kategoriler için özel işlem yapar.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        val channels: Flow<List<LiveStreamEntity>> =
            _selectedCategoryId
                .asStateFlow()
                .flatMapLatest { categoryIdString ->
                    val categoryId = categoryIdString?.toIntOrNull()
                    if (categoryId != null) {
                        when (categoryId) {
                            VIRTUAL_CATEGORY_ID_FAVORITES -> {
                                // Favoriler kategorisi için
                                favoriteChannelIds.flatMapLatest { favoriteIds ->
                                    if (favoriteIds.isNotEmpty()) {
                                        kotlinx.coroutines.flow.flow {
                                            val channels = contentRepository.getLiveStreamsByIds(favoriteIds)
                                            emit(channels)
                                        }
                                    } else {
                                        kotlinx.coroutines.flow.flowOf(emptyList())
                                    }
                                }
                            }
                            VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED -> {
                                // Son İzlenenler kategorisi için
                                recentlyWatchedChannelIds.flatMapLatest { recentlyWatchedIds ->
                                    if (recentlyWatchedIds.isNotEmpty()) {
                                        kotlinx.coroutines.flow.flow {
                                            val channels = contentRepository.getLiveStreamsByIds(recentlyWatchedIds)
                                            emit(channels)
                                        }
                                    } else {
                                        kotlinx.coroutines.flow.flowOf(emptyList())
                                    }
                                }
                            }
                            else -> {
                                // Normal kategori
                                contentRepository.getLiveStreamsByCategoryId(categoryId)
                            }
                        }
                    } else {
                        kotlinx.coroutines.flow.flowOf(emptyList())
                    }
                }

        /**
         * İçerikleri yükler.
         */
        fun loadContent() {
            viewModelScope.launch {
                // Result'u kontrol et, ancak hata durumunu UI'da göstermek için
                // ViewModel içinde bir hata state'i tutulabilir veya
                // bu işlem sessizce yapılabilir
                contentRepository.refreshLiveStreams()
            }
        }

        /**
         * Kategori seçer.
         * String categoryId (CategoryItem'dan) alır.
         */
        fun selectCategory(categoryId: String?) {
            _selectedCategoryId.value = categoryId
        }

        /**
         * Kanalı favorilere ekler.
         */
        fun addFavorite(channelId: Int) {
            viewModelScope.launch {
                contentRepository.addFavorite(channelId, defaultViewerId)
                showToast(context.getString(R.string.toast_favorite_added))
            }
        }

        /**
         * Kanalı favorilerden çıkarır.
         */
        fun removeFavorite(channelId: Int) {
            viewModelScope.launch {
                contentRepository.removeFavorite(channelId, defaultViewerId)
                showToast(context.getString(R.string.toast_favorite_removed))
            }
        }

        /**
         * Kanalın favori olup olmadığını kontrol eder.
         */
        fun isFavorite(channelId: Int): Flow<Boolean> = contentRepository.isFavorite(channelId, defaultViewerId)

        // Channel selection event - URL building için
        private val _openPlayerEvent = MutableSharedFlow<Pair<String, Int>>()
        val openPlayerEvent: SharedFlow<Pair<String, Int>> = _openPlayerEvent.asSharedFlow()

        /**
         * Kanal seçildiğinde çağrılır.
         * Stream URL'si oluşturulur ve PlayerActivity'yi açmak için event emit edilir.
         *
         * @param channel Seçilen kanal
         */
        fun onChannelSelected(channel: LiveStreamEntity) {
            viewModelScope.launch {
                val url = buildLiveStreamUrlUseCase(channel)
                if (url != null) {
                    timber.log.Timber.d("📡 CANLI YAYIN URL: $url")
                    _openPlayerEvent.emit(Pair(url, channel.streamId))
                } else {
                    timber.log.Timber.e("❌ Canlı yayın stream URL oluşturulamadı!")
                }
            }
        }
    }
