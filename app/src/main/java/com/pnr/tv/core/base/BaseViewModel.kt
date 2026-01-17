package com.pnr.tv.core.base

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pnr.tv.repository.Result
import com.pnr.tv.util.error.ErrorHelper
import com.pnr.tv.util.error.ErrorSeverity
import com.pnr.tv.util.error.Resource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tüm ViewModel'ler için ortak işlevleri içeren base sınıf.
 *
 * Bu sınıf, ViewModel'lerde sık kullanılan ortak işlevleri sağlar:
 * - Toast event yönetimi
 * - Context erişimi
 * - Helper metodlar
 */
abstract class BaseViewModel : ViewModel() {
    // Son odaklanılan içeriğin pozisyonunu saklamak için ortak değişken
    var lastFocusedContentPosition: Int? = null

    // Son seçili kategori ID'sini saklamak için ortak değişken
    var lastSelectedCategoryId: String? = null

    // Veri hazır mı? (liste çizilmeden önce navbar/back tuşu focusable olmamalı)
    var isDataReady: Boolean = false
        set(value) {
            field = value
            // Log eklenebilir
        }

    /**
     * Toast mesajları için SharedFlow.
     * Child ViewModel'ler bu flow'u kullanarak toast mesajları gönderebilir.
     */
    protected val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    /**
     * Error mesajı için StateFlow.
     * Child ViewModel'ler bu flow'u kullanarak error mesajları gösterebilir.
     */
    protected val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Error severity için StateFlow.
     * Error mesajının otomatik kapanma süresini belirler.
     */
    protected val _errorSeverity = MutableStateFlow<ErrorSeverity>(ErrorSeverity.MEDIUM)
    val errorSeverity: StateFlow<ErrorSeverity> = _errorSeverity.asStateFlow()

    /**
     * Toast mesajı gönderir.
     *
     * @param message Gösterilecek mesaj
     */
    protected fun showToast(message: String) {
        viewModelScope.launch {
            _toastEvent.emit(message)
        }
    }

    /**
     * Error mesajını ayarlar.
     * ErrorHelper'dan gelen Result.Error'dan mesaj ve severity alır.
     *
     * @param error Result.Error
     */
    protected fun setError(error: Result.Error) {
        _errorMessage.value = error.message
        _errorSeverity.value = error.severity
    }

    /**
     * Error mesajını ayarlar (manuel).
     *
     * @param message Hata mesajı
     * @param severity Hata şiddeti (varsayılan: MEDIUM)
     */
    protected fun setError(
        message: String,
        severity: ErrorSeverity = ErrorSeverity.MEDIUM,
    ) {
        _errorMessage.value = message
        _errorSeverity.value = severity
    }

    /**
     * Error mesajını temizler.
     */
    fun clearError() {
        _errorMessage.value = null
        _errorSeverity.value = ErrorSeverity.MEDIUM
    }

    /**
     * Exception'dan error oluşturur ve ayarlar.
     * ErrorHelper kullanarak kullanıcı dostu mesaj oluşturur.
     *
     * @param exception Exception
     * @param errorContext Error context (ErrorContext veya String)
     */
    protected fun handleError(
        exception: Throwable,
        errorContext: Any? = null,
    ) {
        val error = ErrorHelper.createError(exception, context, errorContext = errorContext)
        setError(error)
    }

    /**
     * Resource.Error'dan error mesajını ayarlar.
     * Repository'den gelen Resource.Error'ları BaseViewModel'in merkezi errorMessage Flow'una aktarır.
     *
     * @param error Resource.Error
     */
    protected fun handleResourceError(error: Resource.Error) {
        _errorMessage.value = error.message
        _errorSeverity.value = error.severity
    }

    /**
     * Context'e erişim için abstract property.
     * Child ViewModel'ler bu property'yi implement etmelidir.
     */
    protected abstract val context: Context
}
