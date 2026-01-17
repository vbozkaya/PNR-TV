package com.pnr.tv.ui.series

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.pnr.tv.R
import com.pnr.tv.util.ui.BackgroundManager
import com.pnr.tv.util.error.ErrorSeverity
import dagger.hilt.android.scopes.FragmentScoped
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * SeriesDetailFragment için UI bileşenlerinin başlatılması ve görünüm yönetimini yapan handler sınıfı.
 *
 * Bu sınıf, fragment içindeki görünüm kurulumu ve state yönetimi mantığını
 * dışarı çıkararak fragment'ın sorumluluklarını azaltır.
 *
 * Sorumlulukları:
 * - View'ların başlatılması (initViews)
 * - Navbar kurulumu (setupNavbar)
 * - Arka plan yükleme (loadBackground)
 * - UI state yönetimi (showError, hideError, showLoading, showContent)
 */
@FragmentScoped
class SeriesDetailViewHandler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        // View references - initViews ile başlatılacak (nullable for safety, no lateinit)
        private var rootView: View? = null
        private var seriesPoster: ImageView? = null
        private var seriesTitle: TextView? = null
        private var seriesRating: TextView? = null
        private var seriesPlot: TextView? = null
        private var seriesCreator: TextView? = null
        private var seriesGenre: TextView? = null
        private var seriesCast: TextView? = null
        private var creatorLayout: View? = null
        private var genreLayout: View? = null
        private var castLayout: View? = null
        private var loadingIndicator: View? = null
        private var emptyStateContainer: View? = null
        private var emptyStateText: TextView? = null
        private var addFavoriteButton: ImageButton? = null
        private var favoritePremiumText: TextView? = null
        private var errorContainer: View? = null
        private var errorMessage: TextView? = null
        private var retryButton: Button? = null
        private var loadingContainer: View? = null
        private var contentScrollView: View? = null

        // Error auto-dismiss job
        private var errorAutoDismissJob: Job? = null

        // Callbacks for navigation
        private var onBackClicked: (() -> Unit)? = null
        private var onHomeClicked: (() -> Unit)? = null
        private var onRetryClicked: (() -> Unit)? = null

        /**
         * View'ları başlatır ve referansları saklar.
         * Fragment'ın onViewCreated metodunda çağrılmalıdır.
         */
        fun initViews(view: View) {
            try {
                rootView = view
                // Content views (like MovieDetailViewHandler)
                seriesPoster = view.findViewById(R.id.img_series_poster) ?: throw IllegalStateException("img_series_poster not found")
                seriesTitle = view.findViewById(R.id.txt_series_title) ?: throw IllegalStateException("txt_series_title not found")
                seriesRating = view.findViewById(R.id.txt_series_rating) ?: throw IllegalStateException("txt_series_rating not found")
                seriesPlot = view.findViewById(R.id.txt_series_plot) ?: throw IllegalStateException("txt_series_plot not found")
                seriesCreator = view.findViewById(R.id.txt_series_creator) ?: throw IllegalStateException("txt_series_creator not found")
                seriesGenre = view.findViewById(R.id.txt_series_genre) ?: throw IllegalStateException("txt_series_genre not found")
                seriesCast = view.findViewById(R.id.txt_series_cast) ?: throw IllegalStateException("txt_series_cast not found")
                creatorLayout = view.findViewById(R.id.layout_creator) ?: throw IllegalStateException("layout_creator not found")
                genreLayout = view.findViewById(R.id.layout_genre) ?: throw IllegalStateException("layout_genre not found")
                castLayout = view.findViewById(R.id.layout_cast) ?: throw IllegalStateException("layout_cast not found")
                loadingIndicator = view.findViewById(R.id.loading_indicator) ?: throw IllegalStateException("loading_indicator not found")
                emptyStateContainer = view.findViewById(R.id.empty_state_container) ?: throw IllegalStateException("empty_state_container not found")
                emptyStateText = view.findViewById(R.id.txt_empty_state) ?: throw IllegalStateException("txt_empty_state not found")
                addFavoriteButton = view.findViewById<ImageButton>(R.id.btn_add_to_favorites)
                favoritePremiumText = view.findViewById(R.id.tv_favorite_premium) ?: throw IllegalStateException("tv_favorite_premium not found")
                errorContainer = view.findViewById(R.id.error_container) ?: throw IllegalStateException("error_container not found")
                errorMessage = view.findViewById(R.id.txt_error_message) ?: throw IllegalStateException("txt_error_message not found")
                retryButton = view.findViewById(R.id.btn_retry) ?: throw IllegalStateException("btn_retry not found")
                loadingContainer = view.findViewById(R.id.loading_container) ?: throw IllegalStateException("loading_container not found")
                contentScrollView = view.findViewById(R.id.content_scroll_view) ?: throw IllegalStateException("content_scroll_view not found")

                // Retry button click listener
                retryButton?.setOnClickListener {
                    onRetryClicked?.invoke()
                }
            } catch (e: Exception) {
                Timber.e(e, "initViews: Failed to initialize views")
                throw e
            }
        }

        /**
         * Navbar'ı kurar ve buton click listener'larını ayarlar.
         */
        fun setupNavbar(
            view: View,
            navbarTitle: String,
            onBackClicked: () -> Unit,
            onHomeClicked: () -> Unit,
        ) {
            this.onBackClicked = onBackClicked
            this.onHomeClicked = onHomeClicked

            val navbarView = view.findViewById<View>(R.id.navbar)
            val titleTextView = navbarView.findViewById<TextView>(R.id.txt_navbar_title)
            titleTextView?.text = navbarTitle

            navbarView.findViewById<View>(R.id.btn_navbar_back)?.setOnClickListener {
                onBackClicked()
            }

            val homeButton = navbarView.findViewById<View>(R.id.btn_navbar_home)
            homeButton?.setOnClickListener {
                onHomeClicked()
            }

            // Home butonundan sağ yön tuşuna basıldığında olayı tüket (focus gitmesin)
            homeButton?.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return@setOnKeyListener true // Olayı tüket
                }
                false
            }

            // Premium yazısını, arama ve sıralama butonlarını gizle (detay sayfasında görünmemeli)
            navbarView.findViewById<TextView>(R.id.tv_navbar_premium)?.visibility = View.GONE
            navbarView.findViewById<android.widget.EditText>(R.id.edt_navbar_search)?.visibility = View.GONE
            navbarView.findViewById<View>(R.id.btn_navbar_filter)?.visibility = View.GONE
        }

        /**
         * Arka plan görselini güvenli bir şekilde yükler.
         * BackgroundManager kullanarak cache'lenmiş görseli yükler.
         */
        fun loadBackground(
            view: View,
            lifecycleOwner: LifecycleOwner,
        ) {
            lifecycleOwner.lifecycleScope.launch {
                Timber.tag(
                    "BACKGROUND",
                ).d("📐 Fragment View - View: ${view.javaClass.simpleName}, Width: ${view.width}, Height: ${view.height}")

                // Önce cache'den kontrol et (hızlı)
                val cached = BackgroundManager.getCachedBackground()
                if (cached != null) {
                    view.background = cached
                    Timber.tag("BACKGROUND").d("✅ Arkaplan uygulandı - Fragment view background: ${view.background?.javaClass?.simpleName}")
                    return@launch
                }

                // Cache'de yoksa yükle
                BackgroundManager.loadBackground(
                    context = context,
                    imageLoader = null, // Artık kullanılmıyor, Glide kullanılıyor
                    onSuccess = { drawable ->
                        view.background = drawable
                        Timber.tag(
                            "BACKGROUND",
                        ).d("✅ Arkaplan uygulandı - Fragment view background: ${view.background?.javaClass?.simpleName}")
                    },
                    onError = {
                        Timber.tag("BACKGROUND").w("⚠️ onError callback çağrıldı, fallback deneniyor...")
                        // Hata durumunda fallback kullan (theme'de zaten tanımlı)
                        val fallback = BackgroundManager.getFallbackBackground(context)
                        if (fallback != null) {
                            view.background = fallback
                        } else {
                            Timber.tag("BACKGROUND").e("❌ Fallback arkaplan da null!")
                        }
                    },
                )
            }
        }

        /**
         * Retry button click listener'ını ayarlar.
         */
        fun setOnRetryClickListener(listener: () -> Unit) {
            onRetryClicked = listener
        }

        /**
         * Error state'i gösterir.
         */
        fun showError(
            message: String,
            severity: ErrorSeverity = ErrorSeverity.MEDIUM,
            lifecycleOwner: LifecycleOwner,
        ) {
            // Önceki auto-dismiss job'ı iptal et
            errorAutoDismissJob?.cancel()

            errorContainer?.visibility = View.VISIBLE
            loadingContainer?.visibility = View.GONE
            contentScrollView?.visibility = View.GONE
            errorMessage?.text = message
            retryButton?.requestFocus()

            // Otomatik kapanma mekanizması (severity'ye göre)
            if (severity.shouldAutoDismiss) {
                errorAutoDismissJob =
                    lifecycleOwner.lifecycleScope.launch {
                        delay(severity.autoDismissDurationMs)
                        // Fragment hala aktifse error state'i kapat
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                            hideError()
                        }
                    }
            }
        }

        /**
         * Error state'i gizler.
         */
        fun hideError() {
            errorAutoDismissJob?.cancel()
            errorAutoDismissJob = null
            errorContainer?.visibility = View.GONE
        }

        /**
         * Loading state'i gösterir.
         */
        fun showLoading() {
            errorContainer?.visibility = View.GONE
            loadingContainer?.visibility = View.VISIBLE
            contentScrollView?.visibility = View.GONE
        }

        /**
         * Content state'i gösterir.
         */
        fun showContent() {
            errorContainer?.visibility = View.GONE
            loadingContainer?.visibility = View.GONE
            contentScrollView?.visibility = View.VISIBLE
        }

        /**
         * View referanslarını döndürür (fragment'tan erişim için).
         * Returns nullable views for safety - callers should check for null.
         */
        fun getSeriesPoster(): ImageView? = seriesPoster

        fun getSeriesTitle(): TextView? = seriesTitle

        fun getSeriesRating(): TextView? = seriesRating

        fun getSeriesPlot(): TextView? = seriesPlot

        fun getSeriesCreator(): TextView? = seriesCreator

        fun getSeriesGenre(): TextView? = seriesGenre

        fun getSeriesCast(): TextView? = seriesCast

        fun getCreatorLayout(): View? = creatorLayout

        fun getGenreLayout(): View? = genreLayout

        fun getCastLayout(): View? = castLayout

        fun getLoadingIndicator(): View? = loadingIndicator

        fun getEmptyStateContainer(): View? = emptyStateContainer

        fun getEmptyStateText(): TextView? = emptyStateText

        fun getAddFavoriteButton(): ImageButton? = addFavoriteButton

        fun getFavoritePremiumText(): TextView? = favoritePremiumText
    }
