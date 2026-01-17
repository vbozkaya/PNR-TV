package com.pnr.tv.core.base

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.R
import com.pnr.tv.extensions.hide
import com.pnr.tv.extensions.show
import com.pnr.tv.extensions.showCustomToast
import com.pnr.tv.ui.browse.ContentAdapter
import com.pnr.tv.ui.browse.SkeletonAdapter
import com.pnr.tv.util.ui.BackgroundManager
import com.pnr.tv.util.ui.setBackgroundSafely
import com.pnr.tv.util.error.ErrorSeverity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Browse ekranlarında gösterilebilecek UI state'leri.
 */
sealed class BrowseUiState {
    data class Empty(val message: String) : BrowseUiState()
    object Content : BrowseUiState()
    object Loading : BrowseUiState()
    data class Error(val message: String, val severity: ErrorSeverity = ErrorSeverity.MEDIUM) : BrowseUiState()
}

/**
 * Browse ekranlarında UI state yönetimini yapan handler sınıfı.
 *
 * Bu sınıf, BaseBrowseFragment içindeki görsel durum (UI State) yönetimi mantığını
 * dışarı çıkararak fragment'ın sorumluluklarını azaltır.
 *
 * Sorumlulukları:
 * - Empty state gösterimi
 * - Content state gösterimi
 * - Loading state gösterimi (skeleton loading)
 * - Error state gösterimi ve yönetimi
 * - SkeletonAdapter yönetimi
 * - Background yükleme
 * - Toast gösterimi
 */
class BrowseUiHandler(
    private val emptyStateTextView: TextView,
    private val contentRecyclerView: RecyclerView,
    private val errorContainer: View?,
    private val errorMessage: TextView?,
    private val retryButton: Button?,
    private val loadingContainer: View?,
    private val emptyStateContainer: View?,
    private val contentAdapter: ContentAdapter,
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val gridColumnCount: Int,
    private val onRetryClicked: () -> Unit,
    private val rootView: View,
    private val toastEventFlow: Flow<String>,
) {
    // Skeleton loading adapter (optional - used during loading state)
    private var skeletonAdapter: SkeletonAdapter? = null

    // Error auto-dismiss job - otomatik kapanma için
    private var errorAutoDismissJob: Job? = null

    init {
        // Setup retry button click listener
        retryButton?.setOnClickListener {
            onRetryClicked()
        }
    }

    /**
     * Arka plan görselini güvenli bir şekilde yükler.
     * BackgroundManager kullanarak cache'lenmiş görseli yükler.
     * Tüm browse fragment'larında otomatik olarak çağrılır.
     */
    fun loadBackground() {
        try {
            lifecycleOwner.lifecycleScope.launch {
                // Önce cache'den kontrol et (hızlı)
                // getCachedBackground zaten recycle kontrolü yapıyor
                val cached = BackgroundManager.getCachedBackground()
                if (cached != null) {
                    rootView.setBackgroundSafely(cached)
                    return@launch
                }

                // Cache'de yoksa yükle
                BackgroundManager.loadBackground(
                    context = context,
                    imageLoader = null, // Artık kullanılmıyor, Glide kullanılıyor
                    onSuccess = { drawable ->
                        // Güvenli set et - ekstra koruma
                        rootView.setBackgroundSafely(drawable)
                    },
                    onError = {
                        // Hata durumunda fallback kullan (theme'de zaten tanımlı)
                        val fallback = BackgroundManager.getFallbackBackground(context)
                        rootView.setBackgroundSafely(fallback)
                    },
                )
            }
        } catch (e: IllegalStateException) {
            // Fragment view'ı yoksa (onDestroyView sonrası) sessizce geç
        }
    }

    /**
     * Toast olaylarını dinler ve gösterir.
     */
    fun setupToastObserver() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                toastEventFlow.collect { message ->
                    context.showCustomToast(message)
                }
            }
        }
    }

    /**
     * Empty state'i gösterir.
     * @param message Gösterilecek boş durum mesajı
     */
    fun showEmptyState(message: String) {
        // Mesaj boş değilse göster
        if (message.isNotEmpty()) {
            emptyStateTextView.text = message
            emptyStateTextView.show()

            // Empty state container varsa onu da göster
            emptyStateContainer?.show()

            // Empty state TextView'ı focusable yap ve focus ver
            emptyStateTextView.isFocusable = true
            emptyStateTextView.isFocusableInTouchMode = true
            emptyStateTextView.requestFocus()
        } else {
            // Mesaj boşsa, varsayılan mesaj kullan
            emptyStateTextView.text = context.getString(R.string.empty_category_content)
            emptyStateTextView.show()
            emptyStateContainer?.show()
            emptyStateTextView.isFocusable = true
            emptyStateTextView.isFocusableInTouchMode = true
            emptyStateTextView.requestFocus()
        }

        contentRecyclerView.hide()
    }

    /**
     * Normal state'i gösterir (empty state'i gizler).
     */
    fun showContentState() {
        emptyStateTextView.hide()
        emptyStateContainer?.hide()

        // Skeleton adapter'ı kaldır ve normal content adapter'ı geri yükle
        if (contentRecyclerView.adapter == skeletonAdapter) {
            contentRecyclerView.adapter = contentAdapter
        }

        contentRecyclerView.show()
        errorContainer?.hide()
        loadingContainer?.hide()

        // Empty state TextView'ın focus özelliğini kapat
        emptyStateTextView.isFocusable = false
        emptyStateTextView.isFocusableInTouchMode = false
    }

    /**
     * Loading state'i gösterir.
     * PLACEHOLDER KORUMASI: Skeleton loading kullanarak içerik grid'inde placeholder gösterir.
     * contentRecyclerView'ı asla hide() yapma - RecyclerView gizlendiği anda odak (focus) sistem tarafından öldürülür.
     */
    fun showLoadingState() {
        // Error ve empty state'i gizle
        errorContainer?.hide()
        emptyStateTextView.hide()

        // Loading container'ı gizle (ProgressBar yerine skeleton kullanıyoruz)
        loadingContainer?.hide()

        // PLACEHOLDER KORUMASI: Content RecyclerView'i göster ve skeleton adapter kullan
        // RecyclerView'ı asla hide() yapma - odak korunması için görünür kalmalı
        if (skeletonAdapter == null) {
            skeletonAdapter =
                SkeletonAdapter(
                    skeletonCount = gridColumnCount * 2, // 2 satır skeleton göster
                )
        }

        // Mevcut adapter'ı sakla ve skeleton adapter'ı göster
        contentRecyclerView.adapter = skeletonAdapter
        contentRecyclerView.show() // PLACEHOLDER KORUMASI: RecyclerView görünür kalmalı
    }

    /**
     * Error state'i gösterir.
     * Tüm ekranlarda tutarlı error gösterimi sağlar.
     * Hata severity'ye göre otomatik kapanma süresi belirlenir.
     *
     * @param message Hata mesajı (kullanıcı dostu formatlanmış olmalı)
     * @param severity Hata şiddeti (varsayılan: MEDIUM - 5 saniye)
     */
    fun showErrorState(
        message: String,
        severity: ErrorSeverity = ErrorSeverity.MEDIUM,
    ) {
        // Önceki auto-dismiss job'ı iptal et
        errorAutoDismissJob?.cancel()

        // Hata mesajını göster (eğer boşsa varsayılan mesaj kullan)
        val displayMessage =
            if (message.isNotEmpty()) {
                message
            } else {
                context.getString(R.string.error_unknown)
            }
        errorMessage?.text = displayMessage

        // Diğer state'leri gizle
        loadingContainer?.hide()

        // Skeleton adapter'ı kaldır ve normal content adapter'ı geri yükle
        if (contentRecyclerView.adapter == skeletonAdapter) {
            contentRecyclerView.adapter = contentAdapter
        }

        contentRecyclerView.hide()
        emptyStateTextView.hide()
        emptyStateContainer?.hide()

        // Error container'ı animasyonlu göster
        errorContainer?.let { container ->
            container.visibility = View.VISIBLE
            container.alpha = 0f
            container.translationY = 50f // Başlangıç pozisyonu (aşağıda)

            // Fade-in ve slide-up animasyonu
            container.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withStartAction {
                    // Animasyon başladığında container görünür olsun
                    container.visibility = View.VISIBLE
                }
                .start()
        }

        // Retry butonuna focus ver (TV için önemli)
        retryButton?.requestFocus()

        // Otomatik kapanma mekanizması (severity'ye göre)
        if (severity.shouldAutoDismiss) {
            errorAutoDismissJob =
                lifecycleOwner.lifecycleScope.launch {
                    delay(severity.autoDismissDurationMs)
                    // Fragment hala aktifse error state'i kapat
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        hideErrorState()
                    }
                }
        }
    }

    /**
     * Error state'i gizler.
     * Otomatik kapanma veya manuel kapatma için kullanılır.
     * Fade-out animasyonu ile kapanır.
     */
    fun hideErrorState() {
        errorAutoDismissJob?.cancel()
        errorAutoDismissJob = null

        // Error container'ı animasyonlu gizle
        errorContainer?.let { container ->
            container.animate()
                .alpha(0f)
                .translationY(-50f) // Yukarı kayarak çık
                .setDuration(200)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    container.visibility = View.GONE
                    container.alpha = 1f // Sonraki gösterim için alpha'yı sıfırla
                    container.translationY = 0f // Sonraki gösterim için translation'ı sıfırla
                }
                .start()
        } ?: run {
            // Container null ise direkt gizle
            errorContainer?.hide()
        }
    }

    /**
     * State-based UI gösterimi. Fragment'tan sadece state gönderilir.
     */
    fun show(state: BrowseUiState) {
        when (state) {
            is BrowseUiState.Empty -> showEmptyState(state.message)
            is BrowseUiState.Content -> showContentState()
            is BrowseUiState.Loading -> showLoadingState()
            is BrowseUiState.Error -> showErrorState(state.message, state.severity)
        }
    }
}
