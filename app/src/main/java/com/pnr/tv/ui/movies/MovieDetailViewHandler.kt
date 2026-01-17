package com.pnr.tv.ui.movies

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
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
import com.pnr.tv.extensions.loadPosterImage
import com.pnr.tv.util.ui.BackgroundManager
import com.pnr.tv.util.error.ErrorSeverity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * MovieDetailFragment için UI bileşenlerinin başlatılması ve görünüm yönetimini yapan handler sınıfı.
 *
 * Bu sınıf, fragment içindeki görünüm kurulumu ve state yönetimi mantığını
 * dışarı çıkararak fragment'ın sorumluluklarını azaltır.
 *
 * Sorumlulukları:
 * - View'ların başlatılması (initViews)
 * - Navbar kurulumu (setupNavbar)
 * - Arka plan yükleme (loadBackground)
 * - UI state yönetimi (showError, hideError, showLoading, showContent)
 * - Play button animasyonları
 */
class MovieDetailViewHandler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        // View references - initViews ile başlatılacak
        private lateinit var rootView: View
        private lateinit var moviePoster: ImageView
        private lateinit var movieTitle: TextView
        private lateinit var movieRating: TextView
        private lateinit var movieDirector: TextView
        private lateinit var movieGenre: TextView
        private lateinit var movieCast: TextView
        private lateinit var moviePlot: TextView
        private lateinit var playButton: Button
        private lateinit var addFavoriteButton: ImageButton
        private lateinit var favoritePremiumText: TextView
        private lateinit var directorLayout: View
        private lateinit var genreLayout: View
        private lateinit var castLayout: View

        // UI State views
        private lateinit var loadingContainer: View
        private lateinit var errorContainer: View
        private lateinit var errorMessage: TextView
        private lateinit var retryButton: Button
        private lateinit var contentGroup: androidx.constraintlayout.widget.Group

        // Error auto-dismiss job
        private var errorAutoDismissJob: Job? = null

        // Callbacks for navigation
        private var onBackClicked: (() -> Unit)? = null
        private var onHomeClicked: (() -> Unit)? = null
        private var onRetryClicked: (() -> Unit)? = null

        // Play button animation state
        private var breathingAnimator: AnimatorSet? = null

        /**
         * View'ları başlatır ve referansları saklar.
         * Fragment'ın onViewCreated metodunda çağrılmalıdır.
         */
        fun initViews(view: View) {
            rootView = view
            // Content views
            moviePoster = view.findViewById(R.id.img_movie_poster)
            movieTitle = view.findViewById(R.id.txt_movie_title)
            movieRating = view.findViewById(R.id.txt_movie_rating)
            movieDirector = view.findViewById(R.id.txt_movie_director)
            movieGenre = view.findViewById(R.id.txt_movie_genre)
            movieCast = view.findViewById(R.id.txt_movie_cast)
            moviePlot = view.findViewById(R.id.txt_movie_plot)
            playButton = view.findViewById(R.id.btn_play)
            addFavoriteButton = view.findViewById(R.id.btn_add_favorite)
            favoritePremiumText = view.findViewById(R.id.tv_favorite_premium)
            directorLayout = view.findViewById(R.id.layout_director)
            genreLayout = view.findViewById(R.id.layout_genre)
            castLayout = view.findViewById(R.id.layout_cast)

            // State views
            loadingContainer = view.findViewById(R.id.loading_container)
            errorContainer = view.findViewById(R.id.error_container)
            errorMessage = view.findViewById(R.id.txt_error_message)
            retryButton = view.findViewById(R.id.btn_retry)
            contentGroup = view.findViewById(R.id.content_group)

            // Retry button click listener
            retryButton.setOnClickListener {
                onRetryClicked?.invoke()
            }

            // Oynat butonuna modern cursor animasyonları ekle
            setupPlayButtonAnimations()
        }

        /**
         * Oynat butonu için akıcı focus animasyonlarını ayarlar
         * Google TV/Apple tvOS tarzı pürüzsüz geçişler
         */
        private fun setupPlayButtonAnimations() {
            playButton.onFocusChangeListener =
                View.OnFocusChangeListener { view, hasFocus ->
                    if (hasFocus) {
                        // Odaklanma animasyonu: Hafif büyütme + Breathing başlat
                        val scaleUpAnim =
                            AnimatorInflater.loadAnimator(
                                context,
                                R.animator.btn_focus_scale_up,
                            ) as AnimatorSet
                        scaleUpAnim.setTarget(view)
                        scaleUpAnim.start()

                        // Breathing animasyonunu başlat (sürekli tekrarlayan)
                        breathingAnimator =
                            AnimatorInflater.loadAnimator(
                                context,
                                R.animator.btn_focus_breathing,
                            ) as AnimatorSet
                        breathingAnimator?.setTarget(view)
                        breathingAnimator?.start()
                    } else {
                        // Odak kaybı animasyonu: Normal boyuta dön
                        breathingAnimator?.cancel()

                        val scaleDownAnim =
                            AnimatorInflater.loadAnimator(
                                context,
                                R.animator.btn_focus_scale_down,
                            ) as AnimatorSet
                        scaleDownAnim.setTarget(view)
                        scaleDownAnim.start()
                    }
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
                // Artık kullanılmıyor, Glide kullanılıyor
                BackgroundManager.loadBackground(
                    context = context,
                    imageLoader = null,
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

            loadingContainer.visibility = View.GONE
            errorContainer.visibility = View.VISIBLE
            contentGroup.visibility = View.GONE
            errorMessage.text = message
            retryButton.requestFocus() // Retry butonuna focus ver

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
            errorContainer.visibility = View.GONE
        }

        /**
         * Loading state'i gösterir.
         */
        fun showLoading() {
            loadingContainer.visibility = View.VISIBLE
            errorContainer.visibility = View.GONE
            contentGroup.visibility = View.GONE
        }

        /**
         * Tüm state container'larını gizler.
         */
        fun hideAllStates() {
            loadingContainer.visibility = View.GONE
            errorContainer.visibility = View.GONE
            contentGroup.visibility = View.GONE
        }

        /**
         * Content state'i gösterir ve verileri doldurur.
         */
        fun showContent(state: MovieDetailUiState.Success) {
            loadingContainer.visibility = View.GONE
            errorContainer.visibility = View.GONE
            contentGroup.visibility = View.VISIBLE

            val movie = state.movie

            // Film başlığı
            movieTitle.text = movie.name ?: ""

            // Rating - Önce IPTV'den, yoksa TMDB'den
            val rating = movie.rating ?: state.rating
            if (rating != null && rating > 0) {
                movieRating.visibility = View.VISIBLE
                movieRating.text = context.getString(R.string.rating_format, rating)
            } else {
                movieRating.visibility = View.GONE
            }

            // Film posteri - Önce IPTV'den, yoksa TMDB'den
            val posterUrl = movie.streamIconUrl ?: state.posterUrl
            // Her zaman maksimum limitlerle yükle (1280x720) - güvenli yaklaşım
            moviePoster.loadPosterImage(imageUrl = posterUrl)

            // Yönetmen
            if (!state.director.isNullOrBlank()) {
                movieDirector.text = state.director
                directorLayout.visibility = View.VISIBLE
            } else {
                directorLayout.visibility = View.GONE
            }

            // Tür
            if (!state.genre.isNullOrBlank()) {
                movieGenre.text = state.genre
                genreLayout.visibility = View.VISIBLE
            } else {
                genreLayout.visibility = View.GONE
            }

            // Oyuncular
            if (!state.cast.isNullOrBlank()) {
                movieCast.text = state.cast
                castLayout.visibility = View.VISIBLE
            } else {
                castLayout.visibility = View.GONE
            }

            // Açıklama (Her zaman göster, boşsa "Açıklama Yok")
            moviePlot.text = state.overview?.takeIf { it.isNotBlank() } ?: context.getString(R.string.no_overview)
        }

        /**
         * View referanslarını döndürür (fragment'tan erişim için).
         */
        fun getPlayButton(): Button = playButton

        fun getAddFavoriteButton(): ImageButton = addFavoriteButton

        fun getFavoritePremiumText(): TextView = favoritePremiumText

        fun getRootView(): View = rootView
    }
