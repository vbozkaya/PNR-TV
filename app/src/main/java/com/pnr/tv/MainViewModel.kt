package com.pnr.tv

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pnr.tv.network.dto.AuthenticationResponseDto
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.repository.Result
import com.pnr.tv.repository.UserRepository
import com.pnr.tv.util.ErrorHelper
import com.pnr.tv.worker.TmdbSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        userRepository: UserRepository,
        private val contentRepository: ContentRepository,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        val currentUser = userRepository.currentUser.asLiveData()
        val userInfo = MutableLiveData<AuthenticationResponseDto?>()

        // Güncelleme durumu için enum
        enum class UpdateState {
            IDLE,
            LOADING,
            COMPLETED,
            ERROR,
        }

        // Güncelleme durumu için StateFlow
        private val _updateState = MutableStateFlow(UpdateState.IDLE)
        val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

        // Hata mesajı için StateFlow
        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        /**
         * Tüm içeriği günceller
         * 
         * Yeni akış:
         * 1. IPTV senkronizasyonu (hızlı, ön planda)
         * 2. WorkManager ile TMDB senkronizasyonu (yavaş, arka planda)
         * 
         * Not: Rate limiting artık ağ katmanında (RateLimiterInterceptor) yapılıyor.
         * UI mantığı (delay, durum güncellemeleri) Fragment/Activity'de yönetilmeli.
         */
        fun refreshAllContent() {
            viewModelScope.launch {
                try {
                    // Yüklenme başladı
                    _updateState.value = UpdateState.LOADING
                    _errorMessage.value = null

                    Timber.d("🚀 HIZLI GÜNCELLEME BAŞLADI (Sadece IPTV)")
                    Timber.d("⚠️ Rate limiting ağ katmanında yapılıyor...")

                    // IPTV içeriklerini yenile (rate limiting interceptor tarafından yönetiliyor)
                    val refreshResult = refreshIptvContent()

                    // Sonuçları işle
                    handleRefreshResult(refreshResult)
                } catch (e: Exception) {
                    // Beklenmeyen hata durumu
                    Timber.e(e, "❌ GÜNCELLEME HATASI")
                    val error = ErrorHelper.createUnexpectedError(e, context)
                    _errorMessage.value = "HATA: ${e.message}\n\n${error.message}"
                    _updateState.value = UpdateState.ERROR
                }
            }
        }

        /**
         * IPTV içeriklerini yeniler (filmler, diziler, canlı yayınlar).
         * Rate limiting ağ katmanında (RateLimiterInterceptor) yapılıyor.
         * 
         * @return Hata listesi, başarılı ise boş liste
         */
        private suspend fun refreshIptvContent(): List<String> {
            val errors = mutableListOf<String>()

            // Filmleri yenile
            val moviesResult = contentRepository.refreshMovies(skipTmdbSync = true)
            if (moviesResult is Result.Error) {
                errors.add("Filmler: ${moviesResult.message}")
            }

            // Dizileri yenile (rate limiting interceptor tarafından yönetiliyor)
            val seriesResult = contentRepository.refreshSeries(skipTmdbSync = true)
            if (seriesResult is Result.Error) {
                errors.add("Diziler: ${seriesResult.message}")
            }

            // Canlı yayınları yenile (rate limiting interceptor tarafından yönetiliyor)
            val liveStreamsResult = contentRepository.refreshLiveStreams()
            if (liveStreamsResult is Result.Error) {
                errors.add("Canlı Yayınlar: ${liveStreamsResult.message}")
            }

            return errors
        }

        /**
         * Yenileme sonuçlarını işler ve durumu günceller.
         * 
         * @param errors Hata listesi, boş ise başarılı
         */
        private suspend fun handleRefreshResult(errors: List<String>) {
            if (errors.isNotEmpty()) {
                _errorMessage.value = errors.joinToString("\n")
                _updateState.value = UpdateState.ERROR
            } else {
                // Canlı kanal resimlerini ön yükle
                contentRepository.preloadAllLiveStreamIcons()

                // IPTV güncelleme tamamlandı - kullanıcı artık uygulamayı kullanabilir
                _updateState.value = UpdateState.COMPLETED

                Timber.d("✅ HIZLI GÜNCELLEME TAMAMLANDI")
                Timber.d("🔄 ARKA PLAN TMDB SENKRONIZASYONU BAŞLATILIYOR...")

                // TMDB senkronizasyonunu arka planda başlat
                scheduleTmdbSync()
            }
        }

        /**
         * TMDB arka plan senkronizasyonunu planlar.
         * WorkManager ile akıllı kategori önceliklendirmesi yapar.
         */
        private fun scheduleTmdbSync() {
            startTmdbBackgroundSync()
        }

        /**
         * Güncelleme durumunu IDLE'a sıfırlar.
         * UI tarafından COMPLETED durumunu gösterdikten sonra çağrılmalıdır.
         */
        fun resetUpdateState() {
            _updateState.value = UpdateState.IDLE
            _errorMessage.value = null
        }

        /**
         * TMDB arka plan senkronizasyonunu başlatır
         * WorkManager ile akıllı kategori önceliklendirmesi yapar
         */
        private fun startTmdbBackgroundSync() {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // İnternet gerekli
                .build()

            val workRequest = OneTimeWorkRequestBuilder<TmdbSyncWorker>()
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(
                        TmdbSyncWorker.INPUT_CONTENT_TYPE to TmdbSyncWorker.CONTENT_TYPE_ALL
                    )
                )
                .build()

            // Mevcut TMDB sync işini iptal et ve yenisini başlat
            WorkManager.getInstance(context).enqueueUniqueWork(
                TmdbSyncWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE, // Eski işi iptal et, yenisini başlat
                workRequest
            )

            Timber.d("📋 WorkManager görevi oluşturuldu ve kuyruğa eklendi")
        }

        fun fetchUserInfo() {
            viewModelScope.launch {
                when (val result = contentRepository.fetchUserInfo()) {
                    is Result.Success -> {
                        userInfo.value = result.data
                    }
                    is Result.Error -> {
                        userInfo.value = null
                    }
                }
            }
        }
    }
