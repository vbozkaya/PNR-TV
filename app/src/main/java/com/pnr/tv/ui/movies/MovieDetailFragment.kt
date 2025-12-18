package com.pnr.tv.ui.movies

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import coil.imageLoader
import coil.load
import coil.size.Scale
import coil.size.Size
import com.pnr.tv.MainActivity
import com.pnr.tv.PlayerActivity
import com.pnr.tv.R
import com.pnr.tv.extensions.normalizeBaseUrl
import com.pnr.tv.repository.UserRepository
import com.pnr.tv.security.DataEncryption
import com.pnr.tv.ui.viewers.SelectViewerDialog
import com.pnr.tv.util.BackgroundManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Film detay sayfası için Fragment.
 * Film bilgilerini gösterir ve oynatma özelliği sunar.
 */
@AndroidEntryPoint
class MovieDetailFragment : Fragment() {
    private lateinit var viewModel: MovieDetailViewModel
    private lateinit var moviePoster: ImageView
    private lateinit var movieTitle: TextView
    private lateinit var movieRating: TextView
    private lateinit var movieDirector: TextView
    private lateinit var movieGenre: TextView
    private lateinit var movieCast: TextView
    private lateinit var moviePlot: TextView
    private lateinit var playButton: Button
    private lateinit var addFavoriteButton: ImageButton
    private lateinit var directorLayout: View
    private lateinit var genreLayout: View
    private lateinit var castLayout: View

    // UI State views
    private lateinit var loadingContainer: View
    private lateinit var errorContainer: View
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: Button
    private lateinit var contentGroup: androidx.constraintlayout.widget.Group

    @Inject
    lateinit var viewModelFactory: MovieDetailViewModel.Factory

    @Inject
    lateinit var userRepository: UserRepository

    // Focus state kaydetme için
    private var lastFocusedViewId: Int = R.id.btn_play

    private val playerActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Oynatıcıdan döndükten sonra yapılacak işlemler
        }

    companion object {
        private const val ARG_MOVIE_ID = "movie_id"
        private const val KEY_LAST_FOCUSED_VIEW_ID = "movie_detail_fragment_last_focused_view_id"

        /**
         * Yeni bir MovieDetailFragment örneği oluşturur.
         */
        fun newInstance(movieId: Int): MovieDetailFragment {
            return MovieDetailFragment().apply {
                arguments =
                    Bundle().apply {
                        putInt(ARG_MOVIE_ID, movieId)
                    }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fragment yeniden yaratıldığında kaydedilen hafızayı geri yükle
        if (savedInstanceState != null) {
            lastFocusedViewId = savedInstanceState.getInt(KEY_LAST_FOCUSED_VIEW_ID, R.id.btn_play)
        }

        viewModel =
            ViewModelProvider(
                this,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(
                        modelClass: Class<T>,
                        extras: androidx.lifecycle.viewmodel.CreationExtras,
                    ): T {
                        return viewModelFactory.create() as T
                    }

                    // Eski API desteği (deprecated ama bazı cihazlarda hala çağrılıyor)
                    @Deprecated("Use create(modelClass, extras) instead", ReplaceWith("create(modelClass, extras)"))
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return viewModelFactory.create() as T
                    }
                },
            )[MovieDetailViewModel::class.java]

        // Film bilgisini yükle
        arguments?.getInt(ARG_MOVIE_ID)?.let { movieId ->
            viewModel.loadMovie(movieId)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Fragment yok edilmeden önce hafızayı kaydet
        outState.putInt(KEY_LAST_FOCUSED_VIEW_ID, lastFocusedViewId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_movie_detail, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        // Arka plan görselini yükle
        loadBackground(view)
        setupNavbar(view)
        setupViews(view)
        setupPlayButton()
        setupFavoriteButton()
        observeUiState()
        observeViewerSelectionDialog()
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.hideTopMenu()

        // Fragment geri geldiğinde son focus edilen view'a focus ver
        view?.post {
            val lastFocusedView = view?.findViewById<View>(lastFocusedViewId)
            if (lastFocusedView != null && lastFocusedView.visibility == View.VISIBLE) {
                lastFocusedView.requestFocus()
            }
        }
    }

    private fun setupNavbar(view: View) {
        val navbarView = view.findViewById<View>(R.id.navbar)
        val titleTextView = navbarView.findViewById<TextView>(R.id.txt_navbar_title)
        titleTextView?.text = getString(R.string.page_movie_details)

        navbarView.findViewById<View>(R.id.btn_navbar_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        navbarView.findViewById<View>(R.id.btn_navbar_home)?.setOnClickListener {
            parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    private fun setupViews(view: View) {
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
        directorLayout = view.findViewById(R.id.layout_director)
        genreLayout = view.findViewById(R.id.layout_genre)
        castLayout = view.findViewById(R.id.layout_cast)

        // State views
        loadingContainer = view.findViewById(R.id.loading_container)
        errorContainer = view.findViewById(R.id.error_container)
        errorMessage = view.findViewById(R.id.txt_error_message)
        retryButton = view.findViewById(R.id.btn_retry)
        contentGroup = view.findViewById(R.id.content_group)

        // Retry button
        retryButton.setOnClickListener {
            arguments?.getInt(ARG_MOVIE_ID)?.let { movieId ->
                viewModel.loadMovie(movieId)
            }
        }

        // Oynat butonuna modern cursor animasyonları ekle
        setupPlayButtonAnimations()
    }

    /**
     * Oynat butonu için akıcı focus animasyonlarını ayarlar
     * Google TV/Apple tvOS tarzı pürüzsüz geçişler
     */
    private fun setupPlayButtonAnimations() {
        var breathingAnimator: AnimatorSet? = null

        playButton.onFocusChangeListener =
            View.OnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    // Odaklanma animasyonu: Hafif büyütme + Breathing başlat
                    val scaleUpAnim =
                        AnimatorInflater.loadAnimator(
                            requireContext(),
                            R.animator.btn_focus_scale_up,
                        ) as AnimatorSet
                    scaleUpAnim.setTarget(view)
                    scaleUpAnim.start()

                    // Breathing animasyonunu başlat (sürekli tekrarlayan)
                    breathingAnimator =
                        AnimatorInflater.loadAnimator(
                            requireContext(),
                            R.animator.btn_focus_breathing,
                        ) as AnimatorSet
                    breathingAnimator?.setTarget(view)
                    breathingAnimator?.start()
                } else {
                    // Odak kaybı animasyonu: Normal boyuta dön
                    breathingAnimator?.cancel()

                    val scaleDownAnim =
                        AnimatorInflater.loadAnimator(
                            requireContext(),
                            R.animator.btn_focus_scale_down,
                        ) as AnimatorSet
                    scaleDownAnim.setTarget(view)
                    scaleDownAnim.start()
                }
            }
    }

    /**
     * UI State'i dinler ve duruma göre ekranı günceller
     * Bu tek metod tüm ekran durumlarını yönetir
     */
    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is MovieDetailUiState.Initial -> {
                            // İlk durum - henüz hiçbir şey gösterme
                            hideAllStates()
                        }

                        is MovieDetailUiState.Loading -> {
                            // Yükleme durumu
                            showLoading()
                        }

                        is MovieDetailUiState.Success -> {
                            // Başarı durumu - verileri göster
                            showContent(state)
                        }

                        is MovieDetailUiState.Error -> {
                            // Hata durumu
                            showError(state.message)
                        }
                    }
                }
            }
        }
    }

    private fun hideAllStates() {
        loadingContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
        contentGroup.visibility = View.GONE
    }

    private fun showLoading() {
        loadingContainer.visibility = View.VISIBLE
        errorContainer.visibility = View.GONE
        contentGroup.visibility = View.GONE
    }

    private fun showError(message: String) {
        loadingContainer.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        contentGroup.visibility = View.GONE
        errorMessage.text = message
        retryButton.requestFocus() // Retry butonuna focus ver
    }

    private fun showContent(state: MovieDetailUiState.Success) {
        loadingContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
        contentGroup.visibility = View.VISIBLE

        val movie = state.movie

        // Film başlığı
        movieTitle.text = movie.name ?: ""

        // Rating
        if (movie.rating != null && movie.rating > 0) {
            movieRating.visibility = View.VISIBLE
            movieRating.text = "${String.format("%.1f", movie.rating)} / 10"
        } else {
            movieRating.visibility = View.GONE
        }

        // Film posteri
        val imageUrl = movie.streamIconUrl
        if (!imageUrl.isNullOrBlank()) {
            moviePoster.load(imageUrl) {
                placeholder(R.drawable.live)
                error(R.drawable.live)
                crossfade(true)
                scale(Scale.FILL)
                // Maksimum boyut sınırı - çok büyük görselleri önlemek için (1280x720 daha güvenli)
                size(Size(1280, 720))
                // Hardware bitmap'leri devre dışı bırak
                allowHardware(false)
                // RGB565 formatını kullan - daha az bellek kullanır
                allowRgb565(true)
            }
        } else {
            moviePoster.load(R.drawable.live) {
                scale(Scale.FILL)
            }
        }

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
        moviePlot.text = state.overview?.takeIf { it.isNotBlank() } ?: getString(R.string.no_overview)

        // Son focus edilen view'a veya play butonuna focus ver
        view?.post {
            val lastFocusedView = view?.findViewById<View>(lastFocusedViewId)
            if (lastFocusedView != null && lastFocusedView.visibility == View.VISIBLE) {
                lastFocusedView.requestFocus()
            } else {
                playButton.requestFocus()
                lastFocusedViewId = R.id.btn_play
            }
        }
    }

    private fun setupPlayButton() {
        // Focus değiştiğinde kaydet
        playButton.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                lastFocusedViewId = R.id.btn_play
            }
        }

        playButton.setOnClickListener {
            // Filmi oynat
            viewLifecycleOwner.lifecycleScope.launch {
                val user = userRepository.currentUser.firstOrNull()
                if (user != null) {
                    // DNS ve password'ü şifre çöz
                    val decryptedDns = DataEncryption.decryptSensitiveData(user.dns, requireContext())
                    val decryptedPassword = DataEncryption.decryptSensitiveData(user.password, requireContext())

                    val baseUrl = decryptedDns.normalizeBaseUrl()

                    // Stream URL'yi oluştur
                    val streamUrl = viewModel.getStreamUrl(baseUrl, user.username, decryptedPassword)
                    if (streamUrl != null) {
                        val movie = viewModel.movie.value
                        timber.log.Timber.d("🎬 FİLM URL: $streamUrl (extension: ${movie?.containerExtension ?: "ts (default)"})")

                        // Kaldığı yerden devam için film ID'sini gönder
                        val contentId = "movie_${movie?.streamId}"

                        val intent =
                            Intent(requireContext(), PlayerActivity::class.java).apply {
                                putExtra(PlayerActivity.EXTRA_VIDEO_URL, streamUrl)
                                putExtra(PlayerActivity.EXTRA_CONTENT_ID, contentId)
                                putExtra(PlayerActivity.EXTRA_CONTENT_TITLE, movie?.name)
                                putExtra(PlayerActivity.EXTRA_CONTENT_RATING, movie?.rating ?: -1.0)
                            }
                        playerActivityLauncher.launch(intent)
                    } else {
                        timber.log.Timber.e("❌ Film stream URL oluşturulamadı!")
                    }
                }
            }
        }
    }

    private fun setupFavoriteButton() {
        // Focus değiştiğinde kaydet
        addFavoriteButton.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                lastFocusedViewId = R.id.btn_add_favorite
            }
        }

        addFavoriteButton.setOnClickListener {
            // Favoriye ekle - izleyici seçim dialog'unu göster
            viewModel.addToFavorites()
        }
    }

    /**
     * Arka plan görselini güvenli bir şekilde yükler.
     * BackgroundManager kullanarak cache'lenmiş görseli yükler.
     */
    private fun loadBackground(view: View) {
        timber.log.Timber.tag("BACKGROUND").d("🎬 MovieDetailFragment.loadBackground() çağrıldı")
        viewLifecycleOwner.lifecycleScope.launch {
            // Fragment'ın kendi root view'ına arkaplan ekle (view.rootView yerine view)
            timber.log.Timber.tag(
                "BACKGROUND",
            ).d("📐 Fragment View - View: ${view.javaClass.simpleName}, Width: ${view.width}, Height: ${view.height}")

            // Önce cache'den kontrol et (hızlı)
            val cached = BackgroundManager.getCachedBackground()
            if (cached != null) {
                timber.log.Timber.tag("BACKGROUND").d("✅ Cache'den arkaplan uygulanıyor (Fragment view)")
                view.background = cached
                timber.log.Timber.tag(
                    "BACKGROUND",
                ).d("✅ Arkaplan uygulandı - Fragment view background: ${view.background?.javaClass?.simpleName}")
                return@launch
            }

            timber.log.Timber.tag("BACKGROUND").d("⏳ Cache'de yok, yükleme başlatılıyor...")

            // Cache'de yoksa yükle
            BackgroundManager.loadBackground(
                context = requireContext(),
                imageLoader = requireContext().imageLoader,
                onSuccess = { drawable ->
                    timber.log.Timber.tag("BACKGROUND").d("✅ onSuccess callback çağrıldı - Drawable: ${drawable.javaClass.simpleName}")
                    view.background = drawable
                    timber.log.Timber.tag(
                        "BACKGROUND",
                    ).d("✅ Arkaplan uygulandı - Fragment view background: ${view.background?.javaClass?.simpleName}")
                },
                onError = {
                    timber.log.Timber.tag("BACKGROUND").w("⚠️ onError callback çağrıldı, fallback deneniyor...")
                    // Hata durumunda fallback kullan (theme'de zaten tanımlı)
                    val fallback = BackgroundManager.getFallbackBackground(requireContext())
                    if (fallback != null) {
                        view.background = fallback
                        timber.log.Timber.tag("BACKGROUND").d("✅ Fallback arkaplan uygulandı")
                    } else {
                        timber.log.Timber.tag("BACKGROUND").e("❌ Fallback arkaplan da null!")
                    }
                },
            )
        }
    }

    private fun observeViewerSelectionDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.showViewerSelectionDialog.collect { viewers ->
                    SelectViewerDialog(
                        context = requireContext(),
                        viewers = viewers,
                        onViewerSelected = { viewer ->
                            viewModel.saveFavoriteForViewer(viewer)
                            android.widget.Toast.makeText(
                                requireContext(),
                                getString(R.string.toast_favorite_added),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                        lifecycleScope = viewLifecycleOwner.lifecycleScope,
                    ).show()
                }
            }
        }
    }
}
