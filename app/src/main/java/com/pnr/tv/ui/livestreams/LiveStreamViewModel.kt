package com.pnr.tv.ui.livestreams

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.pnr.tv.R
import com.pnr.tv.core.constants.UIConstants
import com.pnr.tv.db.entity.LiveStreamCategoryEntity
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.domain.BuildLiveStreamUrlUseCase
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.repository.LiveStreamRepository
import com.pnr.tv.repository.Result
import com.pnr.tv.core.base.BaseViewModel
import com.pnr.tv.util.error.ErrorHelper
import com.pnr.tv.util.error.Resource
import com.pnr.tv.util.validation.AdultContentDetector
import com.pnr.tv.util.validation.AdultContentPreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Canlı yayınlar için ViewModel.
 *
 * Canlı yayın kategorileri, kanallar ve player navigation işlemlerini yönetir.
 */
@HiltViewModel
class LiveStreamViewModel
    @Inject
    constructor(
        private val liveStreamRepository: LiveStreamRepository,
        private val buildLiveStreamUrlUseCase: BuildLiveStreamUrlUseCase,
        private val adultContentPreferenceManager: AdultContentPreferenceManager,
        @ApplicationContext override val context: Context,
    ) : BaseViewModel() {
        // Canlı yayın kategorileri
        val liveStreamCategories: Flow<List<CategoryItem>> =
            combine(
                liveStreamRepository.getLiveStreamCategories().map { resource ->
                    when (resource) {
                        is Resource.Loading -> emptyList<LiveStreamCategoryEntity>()
                        is Resource.Success -> {
                            clearError()
                            resource.data
                        }
                        is Resource.Error -> {
                            handleResourceError(resource)
                            emptyList<LiveStreamCategoryEntity>()
                        }
                    }
                },
                adultContentPreferenceManager.isAdultContentEnabled().onStart { emit(false) },
                liveStreamRepository.getLiveStreams().map { resource ->
                    when (resource) {
                        is Resource.Loading -> emptyList<LiveStreamEntity>()
                        is Resource.Success -> {
                            clearError()
                            resource.data
                        }
                        is Resource.Error -> {
                            handleResourceError(resource)
                            emptyList<LiveStreamEntity>()
                        }
                    }
                }, // Tüm kanalları al (filtrelenmemiş)
            ) { normalCategories: List<LiveStreamCategoryEntity>,
                adultContentEnabled: Boolean,
                allStreams: List<LiveStreamEntity>,
                ->
                // Yetişkin içerik ayarına göre filtrelenmiş kanallar
                val filteredStreams =
                    if (adultContentEnabled) {
                        allStreams
                    } else {
                        allStreams.filter { it.isAdult != true }
                    }

                // Kategori ID'ye göre kanal sayısını hesapla (null categoryId'leri filtrele)
                val categoryStreamCounts =
                    filteredStreams
                        .filter { it.categoryId != null } // Null categoryId'leri filtrele
                        .groupBy { it.categoryId!! } // Null olmayan categoryId'leri garanti et
                        .mapValues { it.value.size }

                val categoriesWithVirtual = mutableListOf<LiveStreamCategoryEntity>()

                // Normal kategoriler - sadece içerik varsa göster
                normalCategories.forEach { category: LiveStreamCategoryEntity ->
                    val categoryId = category.categoryIdInt
                    val streamCount = categoryStreamCounts[categoryId] ?: 0

                    // Kategori adına göre yetişkin içerik kontrolü
                    val isAdultCategory = AdultContentDetector.isAdultCategory(category.categoryName) == true

                    // Yetişkin içerik ayarı kapalıysa ve kategori yetişkin içerik ise, kategoriyi gizle
                    if (!adultContentEnabled && isAdultCategory) {
                        // Kategori gizlendi
                    } else if (streamCount > 0) {
                        // İçerik varsa ve (yetişkin içerik ayarı açık veya kategori yetişkin içerik değilse) göster
                        categoriesWithVirtual.add(category)
                    }
                }

                categoriesWithVirtual
            }.flowOn(Dispatchers.Default) // Heavy computations on background thread

        // Seçili kategori ID (String format - CategoryAdapter için)
        private val _selectedLiveStreamCategoryId = MutableStateFlow<String?>(null)
        val selectedLiveStreamCategoryId: StateFlow<String?> = _selectedLiveStreamCategoryId.asStateFlow()

        // Canlı yayınlar için loading state
        private val _isLiveStreamsLoading = MutableStateFlow(false)
        val isLiveStreamsLoading: StateFlow<Boolean> = _isLiveStreamsLoading.asStateFlow()

        // Canlı yayınlar için error state
        private val _liveStreamsErrorMessage = MutableStateFlow<String?>(null)
        val liveStreamsErrorMessage: StateFlow<String?> = _liveStreamsErrorMessage.asStateFlow()

        // Arama sorgusu
        private val _liveStreamSearchQuery = MutableStateFlow<String>("")
        val liveStreamSearchQuery: StateFlow<String> = _liveStreamSearchQuery.asStateFlow()

        // Search query için debounce'lu flow
        @OptIn(FlowPreview::class)
        private val debouncedLiveStreamSearchQuery: Flow<String> =
            _liveStreamSearchQuery.asStateFlow().debounce(
                UIConstants.DelayDurations.SEARCH_DEBOUNCE_MS,
            )

        // Seçili kategoriye ait kanalları döndürür
        @OptIn(ExperimentalCoroutinesApi::class)
        private val rawLiveStreams: Flow<List<LiveStreamEntity>> =
            combine(
                debouncedLiveStreamSearchQuery.onStart { emit("") },
                _selectedLiveStreamCategoryId.asStateFlow(),
            ) { query, categoryIdString ->
                // Arama yapılıyorsa (3+ karakter), tüm kanalları al
                if (query.length >= 3) {
                    Pair(true, null)
                } else {
                    // Arama yapılmıyorsa, seçili kategorideki kanalları al
                    Pair(false, categoryIdString)
                }
            }.flatMapLatest { (isSearch, categoryIdString) ->
                if (isSearch) {
                    // Arama modu: Tüm kanalları al
                    liveStreamRepository.getLiveStreams().map { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                _isLiveStreamsLoading.value = true
                                emptyList<LiveStreamEntity>()
                            }
                            is Resource.Success -> {
                                _isLiveStreamsLoading.value = false
                                clearError()
                                resource.data
                            }
                            is Resource.Error -> {
                                _isLiveStreamsLoading.value = false
                                handleResourceError(resource)
                                emptyList<LiveStreamEntity>()
                            }
                        }
                    }
                } else {
                    // Normal mod: Seçili kategorideki kanalları al
                    val categoryId = categoryIdString?.toIntOrNull()
                    if (categoryId != null) {
                        liveStreamRepository.getLiveStreamsByCategoryId(categoryId).map { resource ->
                            when (resource) {
                                is Resource.Loading -> {
                                    _isLiveStreamsLoading.value = true
                                    emptyList<LiveStreamEntity>()
                                }
                                is Resource.Success -> {
                                    _isLiveStreamsLoading.value = false
                                    clearError()
                                    resource.data
                                }
                                is Resource.Error -> {
                                    _isLiveStreamsLoading.value = false
                                    handleResourceError(resource)
                                    emptyList<LiveStreamEntity>()
                                }
                            }
                        }
                    } else {
                        kotlinx.coroutines.flow.flowOf(emptyList())
                    }
                }
            }

        // Yetişkin içerik ve arama filtresi ile kanalları döndürür
        val liveStreams: Flow<List<LiveStreamEntity>> =
            combine(
                rawLiveStreams.onStart { emit(emptyList()) },
                adultContentPreferenceManager.isAdultContentEnabled().onStart { emit(false) },
                debouncedLiveStreamSearchQuery.onStart { emit("") },
            ) { streams, adultContentEnabled, query ->
                // Önce yetişkin içerik filtresini uygula
                val adultFilteredStreams =
                    if (adultContentEnabled) {
                        streams
                    } else {
                        streams.filter { stream ->
                            stream.isAdult != true
                        }
                    }

                // Sonra arama filtresini uygula
                applySearchFilter(adultFilteredStreams, query)
            }.flowOn(Dispatchers.Default) // Heavy computations on background thread

        /**
         * İçerik listesine arama filtresi uygular.
         *
         * @param items Filtrelenecek içerik listesi
         * @param query Arama sorgusu
         * @return Filtrelenmiş içerik listesi
         */
        private fun applySearchFilter(
            items: List<LiveStreamEntity>,
            query: String,
        ): List<LiveStreamEntity> {
            if (query.length < 3) {
                return items
            }

            val lowerQuery = query.lowercase()
            return items.filter { item ->
                item.title.lowercase().contains(lowerQuery)
            }
        }

        // Channel selection event - URL building için
        private val _openPlayerEvent = MutableSharedFlow<Triple<String, Int, Int?>>()
        val openPlayerEvent: SharedFlow<Triple<String, Int, Int?>> = _openPlayerEvent.asSharedFlow()

        /**
         * Canlı yayınları yükler (eğer daha önce yüklenmemişse).
         * İçerikler sadece yoksa yüklenir (performans için).
         * Kategoriler sadece ana sayfadan "Güncelle" butonuna basıldığında güncellenir.
         *
         * CACHE KONTROLÜ: Eğer veriler zaten yüklüyse, loading state'i tetiklemez ve
         * Flow'ları gereksiz yere yeniden yüklemez. Veriler veritabanından okunur.
         */
        fun loadLiveStreamsIfNeeded() {
            viewModelScope.launch {
                _liveStreamsErrorMessage.value = null

                // Önce veritabanında içerik verisi olup olmadığını kontrol et
                val hasData = liveStreamRepository.hasLiveStreams()

                // Eğer veriler zaten yüklüyse, hiçbir şey yapma
                // Veriler veritabanından Flow'lar aracılığıyla otomatik olarak gösterilir
                if (hasData) {
                    // Loading state'i tetikleme - veriler zaten yüklü
                    return@launch
                }

                // Veriler yoksa, normal yükleme işlemini yap
                _isLiveStreamsLoading.value = true

                try {
                    val result = liveStreamRepository.refreshLiveStreams()
                    if (result is Result.Error) {
                        // ErrorHelper'dan gelen kullanıcı dostu mesajı kullan
                        _liveStreamsErrorMessage.value =
                            result.message.ifEmpty {
                                context.getString(R.string.error_live_streams_short)
                            }
                    }
                } catch (e: Exception) {
                    // ErrorHelper kullanarak kullanıcı dostu mesaj oluştur
                    val errorResult =
                        ErrorHelper.createError(
                            exception = e,
                            context = context,
                            errorContext = "LiveStreamViewModel",
                        )
                    _liveStreamsErrorMessage.value = errorResult.message
                } finally {
                    _isLiveStreamsLoading.value = false
                }
            }
        }

        /**
         * Sadece kategorileri günceller (içerikleri yüklemez).
         * Fragment onResume() içinde çağrılır, kaynak değişikliklerini yansıtmak için.
         */
        fun refreshCategoriesOnly() {
            viewModelScope.launch {
                try {
                    val categoriesResult = liveStreamRepository.refreshLiveStreamCategories()
                    if (categoriesResult is Result.Error) {
                        // Kategori güncelleme hatası - sessizce devam et
                    }
                } catch (e: Exception) {
                    // Kategori güncelleme hatası - sessizce devam et
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
         * Kanal seçildiğinde çağrılır.
         */
        fun onChannelSelected(channel: LiveStreamEntity) {
            viewModelScope.launch {
                val url = buildLiveStreamUrlUseCase(channel)
                if (url != null && url.isNotBlank()) {
                    _openPlayerEvent.emit(Triple(url, channel.streamId, channel.categoryId))
                } else {
                    showToast(context.getString(R.string.error_video_url_not_found))
                    timber.log.Timber.e("❌ Canlı yayın URL oluşturulamadı: channelId=${channel.streamId}, channelName=${channel.name}")
                }
            }
        }

        /**
         * Canlı yayın arama sorgusunu günceller.
         */
        fun onLiveStreamSearchQueryChanged(query: String) {
            _liveStreamSearchQuery.value = query
        }
    }
