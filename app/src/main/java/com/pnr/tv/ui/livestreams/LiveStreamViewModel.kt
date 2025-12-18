package com.pnr.tv.ui.livestreams

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.pnr.tv.ContentConstants
import com.pnr.tv.R
import com.pnr.tv.db.entity.LiveStreamCategoryEntity
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.domain.BuildLiveStreamUrlUseCase
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.repository.Result
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Canlı yayınlar için ViewModel.
 *
 * Canlı yayın kategorileri, kanallar, favoriler ve player navigation işlemlerini yönetir.
 */
@HiltViewModel
class LiveStreamViewModel
    @Inject
    constructor(
        private val contentRepository: ContentRepository,
        private val buildLiveStreamUrlUseCase: BuildLiveStreamUrlUseCase,
        @ApplicationContext override val context: Context,
    ) : BaseViewModel() {
        companion object {
            // Sanal kategori ID'leri - Canlı Yayınlar için
            val VIRTUAL_CATEGORY_ID_FAVORITES = ContentConstants.VirtualCategoryIdsInt.FAVORITES
            val VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED = ContentConstants.VirtualCategoryIdsInt.RECENTLY_WATCHED
        }

        // For now, use default viewer (id = 1)
        private val defaultViewerId = 1
        private val favoriteChannelIds: Flow<List<Int>> = contentRepository.getFavoriteChannelIds(defaultViewerId)
        private val recentlyWatchedChannelIds: Flow<List<Int>> = contentRepository.getRecentlyWatchedChannelIds()

        // Canlı yayın kategorileri
        val liveStreamCategories: Flow<List<CategoryItem>> =
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
                        sortOrder = ContentConstants.SortOrder.FAVORITES,
                    ),
                )

                // Son İzlenenler kategorisini her zaman ekle
                categoriesWithVirtual.add(
                    LiveStreamCategoryEntity(
                        categoryIdInt = VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED,
                        categoryName = context.getString(R.string.category_recently_watched),
                        sortOrder = ContentConstants.SortOrder.ALL,
                    ),
                )

                // Normal kategorileri ekle
                categoriesWithVirtual.addAll(normalCategories)

                categoriesWithVirtual
            }

        // Seçili kategori ID (String format - CategoryAdapter için)
        private val _selectedLiveStreamCategoryId = MutableStateFlow<String?>(null)
        val selectedLiveStreamCategoryId: StateFlow<String?> = _selectedLiveStreamCategoryId.asStateFlow()

        // Canlı yayınlar için loading state
        private val _isLiveStreamsLoading = MutableStateFlow(false)
        val isLiveStreamsLoading: StateFlow<Boolean> = _isLiveStreamsLoading.asStateFlow()

        // Canlı yayınlar için error state
        private val _liveStreamsErrorMessage = MutableStateFlow<String?>(null)
        val liveStreamsErrorMessage: StateFlow<String?> = _liveStreamsErrorMessage.asStateFlow()

        // Seçili kategoriye ait kanalları döndürür
        @OptIn(ExperimentalCoroutinesApi::class)
        val liveStreams: Flow<List<LiveStreamEntity>> =
            _selectedLiveStreamCategoryId
                .asStateFlow()
                .flatMapLatest { categoryIdString ->
                    val categoryId = categoryIdString?.toIntOrNull()
                    Timber.tag("GRID_UPDATE").d("📡 liveStreams Flow tetiklendi: categoryId=$categoryIdString")
                    if (categoryId != null) {
                        when (categoryId) {
                            VIRTUAL_CATEGORY_ID_FAVORITES -> {
                                favoriteChannelIds.flatMapLatest { favoriteIds ->
                                    if (favoriteIds.isNotEmpty()) {
                                        flow {
                                            val channels = contentRepository.getLiveStreamsByIds(favoriteIds)
                                            emit(channels)
                                        }
                                    } else {
                                        kotlinx.coroutines.flow.flowOf(emptyList())
                                    }
                                }
                            }
                            VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED -> {
                                recentlyWatchedChannelIds.flatMapLatest { recentlyWatchedIds ->
                                    if (recentlyWatchedIds.isNotEmpty()) {
                                        flow {
                                            val channels = contentRepository.getLiveStreamsByIds(recentlyWatchedIds)
                                            emit(channels)
                                        }
                                    } else {
                                        kotlinx.coroutines.flow.flowOf(emptyList())
                                    }
                                }
                            }
                            else -> {
                                contentRepository.getLiveStreamsByCategoryId(categoryId)
                            }
                        }
                    } else {
                        kotlinx.coroutines.flow.flowOf(emptyList())
                    }
                }

        // Channel selection event - URL building için
        private val _openPlayerEvent = MutableSharedFlow<Triple<String, Int, Int?>>()
        val openPlayerEvent: SharedFlow<Triple<String, Int, Int?>> = _openPlayerEvent.asSharedFlow()

        /**
         * Canlı yayınları yükler (eğer daha önce yüklenmemişse).
         * Ana sayfada zaten yüklenmişse, tekrar yüklemez.
         */
        fun loadLiveStreamsIfNeeded() {
            viewModelScope.launch {
                _liveStreamsErrorMessage.value = null
                
                // Önce veritabanında veri olup olmadığını kontrol et
                val hasData = contentRepository.hasLiveStreams()
                val hasCategories = contentRepository.hasLiveStreamCategories()
                
                if (hasData && hasCategories) {
                    // Veri zaten var, yükleme yapma
                    Timber.d("📡 Canlı yayınlar zaten yüklü, tekrar yükleme atlanıyor")
                    _isLiveStreamsLoading.value = false
                    return@launch
                }
                
                // Veri yok, yükleme yap
                _isLiveStreamsLoading.value = true
                Timber.d("📡 Canlı yayınlar yükleniyor (veri yok veya eksik)")

                try {
                    val result = contentRepository.refreshLiveStreams()
                    if (result is Result.Error) {
                        _liveStreamsErrorMessage.value = context.getString(R.string.error_live_streams_short)
                    }
                } catch (e: Exception) {
                    _liveStreamsErrorMessage.value = context.getString(R.string.error_unknown)
                } finally {
                    _isLiveStreamsLoading.value = false
                }
            }
        }

        /**
         * Kategori seçer (Canlı Yayınlar için).
         */
        fun selectLiveStreamCategory(categoryId: String?) {
            _selectedLiveStreamCategoryId.value = categoryId
        }

        /**
         * Kanalı favorilere ekler.
         */
        fun addLiveStreamFavorite(channelId: Int) {
            viewModelScope.launch {
                contentRepository.addFavorite(channelId, defaultViewerId)
                showToast(context.getString(R.string.toast_favorite_added))
            }
        }

        /**
         * Kanalı favorilerden çıkarır.
         */
        fun removeLiveStreamFavorite(channelId: Int) {
            viewModelScope.launch {
                contentRepository.removeFavorite(channelId, defaultViewerId)
                showToast(context.getString(R.string.toast_favorite_removed))
            }
        }

        /**
         * Kanalın favori olup olmadığını kontrol eder.
         */
        fun isLiveStreamFavorite(channelId: Int): Flow<Boolean> = contentRepository.isFavorite(channelId, defaultViewerId)

        /**
         * Kanal seçildiğinde çağrılır.
         */
        fun onChannelSelected(channel: LiveStreamEntity) {
            viewModelScope.launch {
                val url = buildLiveStreamUrlUseCase(channel)
                if (url != null) {
                    Timber.d("📡 CANLI YAYIN URL: $url")
                    _openPlayerEvent.emit(Triple(url, channel.streamId, channel.categoryId))
                } else {
                    Timber.e("❌ Canlı yayın stream URL oluşturulamadı!")
                }
            }
        }
    }
