package com.pnr.tv.ui.shared

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pnr.tv.R
import com.pnr.tv.network.dto.AuthenticationResponseDto
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.repository.Result
import com.pnr.tv.repository.UserRepository
import com.pnr.tv.ui.base.BaseViewModel
import com.pnr.tv.worker.TmdbSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Paylaşılan işlemler için ViewModel.
 *
 * İçerik yenileme, kullanıcı bilgileri ve genel durum yönetimi gibi
 * birden fazla ekranın kullandığı ortak işlemleri yönetir.
 */
@HiltViewModel
class SharedViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
        private val contentRepository: ContentRepository,
        @ApplicationContext override val context: Context,
    ) : BaseViewModel() {
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

        // Hata mesajı için StateFlow (ana güncelleme için)
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
                    val refreshResult =
                        try {
                            refreshIptvContent()
                        } catch (e: Exception) {
                            // refreshIptvContent içindeki hataları yakala
                            Timber.e(e, "❌ REFRESH IPTV CONTENT HATASI")
                            context.getString(R.string.error_server_error)
                        }

                    // Sonuçları işle
                    handleRefreshResult(refreshResult)
                } catch (e: Exception) {
                    // Beklenmeyen hata durumu - uygulamanın kapanmasını önle
                    Timber.e(e, "❌ GÜNCELLEME HATASI")
                    _errorMessage.value = context.getString(R.string.error_server_error)
                    _updateState.value = UpdateState.ERROR
                }
            }
        }

        /**
         * IPTV içeriklerini yeniler (filmler, diziler, canlı yayınlar).
         * Rate limiting ağ katmanında (RateLimiterInterceptor) yapılıyor.
         *
         * @return Hata mesajı, başarılı ise null
         */
        private suspend fun refreshIptvContent(): String? {
            return try {
                // Önce kullanıcı kontrolü yap
                val allUsers =
                    try {
                        userRepository.allUsers.firstOrNull() ?: emptyList()
                    } catch (e: Exception) {
                        Timber.e(e, "Kullanıcı listesi alınamadı")
                        emptyList()
                    }

                if (allUsers.isEmpty()) {
                    return context.getString(R.string.error_user_not_exists)
                }

                val currentUser =
                    try {
                        userRepository.currentUser.firstOrNull()
                    } catch (e: Exception) {
                        Timber.e(e, "Mevcut kullanıcı alınamadı")
                        null
                    }

                if (currentUser == null) {
                    return context.getString(R.string.error_user_not_selected)
                }

                // Filmleri yenile
                val moviesResult =
                    try {
                        contentRepository.refreshMovies(skipTmdbSync = true, forMainScreenUpdate = true)
                    } catch (e: Exception) {
                        Timber.e(e, "Filmler yenilenirken hata")
                        Result.Error(message = context.getString(R.string.error_server_error), exception = e)
                    }
                if (moviesResult is Result.Error) {
                    return moviesResult.message
                }

                // Dizileri yenile (rate limiting interceptor tarafından yönetiliyor)
                val seriesResult =
                    try {
                        contentRepository.refreshSeries(skipTmdbSync = true, forMainScreenUpdate = true)
                    } catch (e: Exception) {
                        Timber.e(e, "Diziler yenilenirken hata")
                        Result.Error(message = context.getString(R.string.error_server_error), exception = e)
                    }
                if (seriesResult is Result.Error) {
                    return seriesResult.message
                }

                // Canlı yayınları yenile (rate limiting interceptor tarafından yönetiliyor)
                val liveStreamsResult =
                    try {
                        contentRepository.refreshLiveStreams(forMainScreenUpdate = true)
                    } catch (e: Exception) {
                        Timber.e(e, "Canlı yayınlar yenilenirken hata")
                        Result.Error(message = context.getString(R.string.error_server_error), exception = e)
                    }
                if (liveStreamsResult is Result.Error) {
                    return liveStreamsResult.message
                }

                null
            } catch (e: Exception) {
                // Beklenmeyen hata - uygulamanın kapanmasını önle
                Timber.e(e, "refreshIptvContent genel hata")
                context.getString(R.string.error_server_error)
            }
        }

        /**
         * Yenileme sonuçlarını işler ve durumu günceller.
         *
         * @param errorMessage Hata mesajı, null ise başarılı
         */
        private suspend fun handleRefreshResult(errorMessage: String?) {
            if (errorMessage != null) {
                _errorMessage.value = errorMessage
                _updateState.value = UpdateState.ERROR
            } else {
                // IPTV güncelleme tamamlandı - kullanıcı artık uygulamayı kullanabilir
                // Not: Kanal ikonları lazy loading ile otomatik olarak yüklenecek (Coil)
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
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED) // İnternet gerekli
                    .build()

            val workRequest =
                OneTimeWorkRequestBuilder<TmdbSyncWorker>()
                    .setConstraints(constraints)
                    .setInputData(
                        workDataOf(
                            TmdbSyncWorker.INPUT_CONTENT_TYPE to TmdbSyncWorker.CONTENT_TYPE_ALL,
                        ),
                    )
                    .build()

            // Mevcut TMDB sync işini iptal et ve yenisini başlat
            WorkManager.getInstance(context).enqueueUniqueWork(
                TmdbSyncWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE, // Eski işi iptal et, yenisini başlat
                workRequest,
            )

            Timber.d("📋 WorkManager görevi oluşturuldu ve kuyruğa eklendi")
        }

        fun fetchUserInfo() {
            viewModelScope.launch {
                try {
                    val result = contentRepository.fetchUserInfo()
                    when (result) {
                        is Result.Success -> {
                            userInfo.value = result.data
                        }
                        is Result.Error -> {
                            Timber.e(result.exception, "Kullanıcı bilgileri alınamadı: ${result.message}")
                            userInfo.value = null
                            _errorMessage.value = result.message
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "fetchUserInfo() coroutine içinde beklenmeyen hata: ${e.javaClass.simpleName}")
                    userInfo.value = null
                }
            }
        }
    }
