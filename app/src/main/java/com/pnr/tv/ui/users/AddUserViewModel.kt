package com.pnr.tv.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pnr.tv.db.entity.UserAccountEntity
import com.pnr.tv.repository.AuthRepository
import com.pnr.tv.repository.Result
import com.pnr.tv.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AddUserViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        /**
         * Yükleniyor durumu - UI'ya loading state'i bildirir.
         */
        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        /**
         * Hata mesajı - UI'ya hata durumunu bildirir.
         */
        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        /**
         * Başarı durumu - UI'ya başarı durumunu bildirir.
         */
        private val _isSuccess = MutableStateFlow(false)
        val isSuccess: StateFlow<Boolean> = _isSuccess.asStateFlow()

        /**
         * Yeni kullanıcı ekler.
         * Önce sunucu doğrulaması yapar, başarılı olursa veritabanına kaydeder.
         *
         * @param user Eklenecek kullanıcı bilgileri
         * @param dns DNS adresi (doğrulama için)
         * @param username Kullanıcı adı (doğrulama için)
         * @param password Şifre (doğrulama için)
         */
        fun addUser(
            user: UserAccountEntity,
            dns: String,
            username: String,
            password: String,
        ) {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = null
                _isSuccess.value = false

                try {
                    // Önce sunucu doğrulaması yap
                    val verificationResult =
                        authRepository.verifyUser(
                            dns = dns,
                            username = username,
                            password = password,
                        )

                    when (verificationResult) {
                        is Result.Success -> {
                            // Doğrulama başarılı, veritabanına kaydet
                            userRepository.addUser(user)
                            _isSuccess.value = true
                        }
                        is Result.Error -> {
                            // Doğrulama başarısız
                            Timber.tag("ADD_USER_FLOW").w("[ViewModel] ❌ Doğrulama başarısız: ${verificationResult.message}")
                            _errorMessage.value = verificationResult.message
                        }
                        is Result.PartialSuccess -> {
                            // Bu durumda kullanılmayacak ama yine de handle et
                            Timber.tag("ADD_USER_FLOW").w("[ViewModel] ⚠️ Kısmi başarı durumu (beklenmeyen)")
                            _errorMessage.value = verificationResult.errorMessage
                                ?: "Kullanıcı doğrulaması başarısız"
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("ADD_USER_FLOW").e(e, "[ViewModel] ❌ Exception yakalandı: ${e.message}")
                    _errorMessage.value = "Kullanıcı eklenirken bir hata oluştu: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }

        /**
         * Mevcut kullanıcıyı günceller.
         * Önce sunucu doğrulaması yapar, başarılı olursa veritabanında günceller.
         *
         * @param user Güncellenecek kullanıcı bilgileri
         * @param dns DNS adresi (doğrulama için)
         * @param username Kullanıcı adı (doğrulama için)
         * @param password Şifre (doğrulama için)
         */
        fun updateUser(
            user: UserAccountEntity,
            dns: String,
            username: String,
            password: String,
        ) {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = null
                _isSuccess.value = false

                try {
                    // Önce sunucu doğrulaması yap
                    val verificationResult =
                        authRepository.verifyUser(
                            dns = dns,
                            username = username,
                            password = password,
                        )

                    when (verificationResult) {
                        is Result.Success -> {
                            // Doğrulama başarılı, veritabanında güncelle
                            userRepository.updateUser(user)
                            _isSuccess.value = true
                        }
                        is Result.Error -> {
                            // Doğrulama başarısız
                            Timber.tag("ADD_USER_FLOW").w("[ViewModel] ❌ Doğrulama başarısız (update): ${verificationResult.message}")
                            _errorMessage.value = verificationResult.message
                        }
                        is Result.PartialSuccess -> {
                            // Bu durumda kullanılmayacak ama yine de handle et
                            Timber.tag("ADD_USER_FLOW").w("[ViewModel] ⚠️ Kısmi başarı durumu (update, beklenmeyen)")
                            _errorMessage.value = verificationResult.errorMessage
                                ?: "Kullanıcı doğrulaması başarısız"
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("ADD_USER_FLOW").e(e, "[ViewModel] ❌ Güncelleme hatası: ${e.message}")
                    _errorMessage.value = "Kullanıcı güncellenirken bir hata oluştu: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }

        /**
         * Hata mesajını temizler.
         */
        fun clearError() {
            _errorMessage.value = null
        }

        /**
         * Başarı durumunu sıfırlar.
         */
        fun clearSuccess() {
            _isSuccess.value = false
        }
    }
