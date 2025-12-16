package com.pnr.tv.ui.base

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /**
     * Toast mesajları için SharedFlow.
     * Child ViewModel'ler bu flow'u kullanarak toast mesajları gönderebilir.
     */
    protected val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

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
     * Context'e erişim için abstract property.
     * Child ViewModel'ler bu property'yi implement etmelidir.
     */
    protected abstract val context: Context
}
