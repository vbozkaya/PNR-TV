package com.pnr.tv.ui.settings

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.pnr.tv.di.IptvRetrofit
import com.pnr.tv.network.dto.AuthenticationResponseDto
import com.pnr.tv.premium.PremiumManager
import com.pnr.tv.repository.ApiServiceManager
import com.pnr.tv.repository.BaseContentRepository
import com.pnr.tv.repository.Result
import com.pnr.tv.repository.UserRepository
import com.pnr.tv.core.base.BaseViewModel
import com.pnr.tv.util.ui.LocaleHelper
import com.pnr.tv.util.ViewerInitializer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import timber.log.Timber
import javax.inject.Inject

/**
 * Settings ekranı için ViewModel.
 * Kullanıcı bilgileri ve hesap durumu yönetimi yapar.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
        private val premiumManager: PremiumManager,
        private val viewerInitializer: ViewerInitializer,
        private val apiServiceManager: ApiServiceManager,
        @ApplicationContext override val context: Context,
    ) : BaseViewModel() {
        // BaseContentRepository instance for fetchUserInfo
        private val baseContentRepository = BaseContentRepository(apiServiceManager, userRepository, context)

        // Current user LiveData - UserRepository'den alınır
        val currentUser = userRepository.currentUser.asLiveData()

        // User info StateFlow - API'den alınan kullanıcı bilgileri
        private val _userInfo = MutableStateFlow<AuthenticationResponseDto?>(null)
        val userInfo: StateFlow<AuthenticationResponseDto?> = _userInfo.asStateFlow()

        // User info LiveData - Fragment'lerde observe etmek için
        val userInfoLiveData: LiveData<AuthenticationResponseDto?> = _userInfo.asLiveData()

        // Premium durumu Flow
        val isPremium = premiumManager.isPremium()

        /**
         * Kullanıcı bilgilerini API'den çeker.
         * SharedViewModel'deki mantığı kullanır.
         */
        fun fetchUserInfo() {
            viewModelScope.launch {
                try {
                    val result = baseContentRepository.fetchUserInfo()
                    when (result) {
                        is Result.Success -> {
                            _userInfo.value = result.data
                        }
                        is Result.PartialSuccess -> {
                            // Kısmi başarı - veriyi kullan ama uyarı göster
                            _userInfo.value = result.data
                            result.errorMessage?.let {
                                Timber.w("Kullanıcı bilgileri kısmen yüklendi: $it")
                            }
                        }
                        is Result.Error -> {
                            Timber.e(result.exception, "Kullanıcı bilgileri alınamadı: ${result.message}")
                            _userInfo.value = null
                            setError(result)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "fetchUserInfo() coroutine içinde beklenmeyen hata: ${e.javaClass.simpleName}")
                    _userInfo.value = null
                }
            }
        }

        /**
         * Önbelleği temizler ve tüm verileri siler.
         * UserRepository ve ViewerInitializer kullanır.
         */
        suspend fun clearCache(): Result<Unit> {
            return try {
                userRepository.clearAllData()
                viewerInitializer.clearInitializationFlag()
                Timber.d("Önbellek başarıyla temizlendi")
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Önbellek temizlenirken hata oluştu")
                Result.Error(
                    message = "Önbellek temizlenirken hata oluştu: ${e.message}",
                    exception = e,
                )
            }
        }

        /**
         * Uygulama dilini değiştirir.
         * @param languageCode Değiştirilecek dil kodu (örn: "tr", "en")
         */
        fun changeLanguage(languageCode: String) {
            LocaleHelper.saveLanguage(context, languageCode)
            Timber.d("Dil değiştirildi: $languageCode")
        }

        /**
         * Mevcut kaydedilmiş dili döndürür.
         */
        fun getCurrentLanguage(): String {
            return LocaleHelper.getSavedLanguage(context)
        }
    }
