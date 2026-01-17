package com.pnr.tv.ui.movies

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import com.pnr.tv.ui.main.MainActivity
import com.pnr.tv.R
import com.pnr.tv.ui.viewers.SelectViewerDialog
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

    @Inject
    lateinit var viewModelFactory: MovieDetailViewModel.Factory

    @Inject
    lateinit var viewHandler: MovieDetailViewHandler

    @Inject
    lateinit var playbackHandler: MoviePlaybackHandler

    // Focus state kaydetme için
    private var lastFocusedViewId: Int = R.id.btn_play

    private val playerActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
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
        // View handler'ı başlat
        viewHandler.initViews(view)
        // Arka plan görselini yükle
        viewHandler.loadBackground(view, viewLifecycleOwner)
        // Navbar'ı kur
        viewHandler.setupNavbar(
            view = view,
            navbarTitle = getString(R.string.page_movie_details),
            onBackClicked = {
                // Activity içindeyse finish, Fragment içindeyse popBackStack
                if (activity is com.pnr.tv.ui.movies.MovieDetailActivity) {
                    requireActivity().finish()
                } else {
                    parentFragmentManager.popBackStack()
                }
            },
            onHomeClicked = {
                // Activity içindeyse finish, Fragment içindeyse popBackStack
                if (activity is com.pnr.tv.ui.movies.MovieDetailActivity) {
                    requireActivity().finish()
                } else {
                    parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                }
            },
        )
        // Retry button listener'ını ayarla
        viewHandler.setOnRetryClickListener {
            arguments?.getInt(ARG_MOVIE_ID)?.let { movieId ->
                viewModel.loadMovie(movieId)
            }
        }
        // Playback handler'ı kur
        playbackHandler.setup(
            fragment = this,
            viewModel = viewModel,
            playerActivityLauncher = playerActivityLauncher,
        )
        // Play button listener'ını ayarla
        setupPlayButton()
        // Favorite button listener'ını ayarla
        setupFavoriteButton()
        // ViewModel gözlemlerini başlat
        observeUiState()
        observeViewerSelectionDialog()
        // Not: Resume dialog kontrolü artık PlayerActivity'de yapılıyor
        observeFavoriteStatus()
        observePremiumStatus()
    }

    override fun onResume() {
        super.onResume()
        // Activity içindeyse BaseActivity zaten navbar'ı yönetiyor
        if (activity !is com.pnr.tv.ui.movies.MovieDetailActivity) {
            (activity as? MainActivity)?.hideTopMenu()
        }

        // Fragment geri geldiğinde son focus edilen view'a focus ver
        view?.post {
            val lastFocusedView = view?.findViewById<View>(lastFocusedViewId)
            if (lastFocusedView != null && lastFocusedView.visibility == View.VISIBLE) {
                lastFocusedView.requestFocus()
            }
        }
    }

    private fun setupPlayButton() {
        // Focus değiştiğinde kaydet
        viewHandler.getPlayButton().setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                lastFocusedViewId = R.id.btn_play
            }
        }

        viewHandler.getPlayButton().setOnClickListener {
            // Filmi oynat - playback handler'a devret
            playbackHandler.playMovie()
        }
    }

    private fun setupFavoriteButton() {
        // Focus değiştiğinde kaydet
        viewHandler.getAddFavoriteButton().setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                lastFocusedViewId = R.id.btn_add_favorite
            }
        }

        viewHandler.getAddFavoriteButton().setOnClickListener {
            // Toggle favori - eğer herhangi bir izleyici için favori ise çıkar, değilse ekle
            viewLifecycleOwner.lifecycleScope.launch {
                // Premium kontrolü - premium değilse işlem yapma
                val isPremium = viewModel.isPremiumSync()
                if (!isPremium) {
                    return@launch
                }

                val isFavorite = viewModel.isFavoriteInAnyViewer().firstOrNull() ?: false

                if (isFavorite) {
                    // Favori ise çıkar
                    viewModel.removeFavoriteForAnyViewer()
                } else {
                    // Favori değilse ekle - izleyici seçim dialog'unu göster
                    viewModel.addToFavorites()
                }
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
                            viewHandler.hideAllStates()
                        }

                        is MovieDetailUiState.Loading -> {
                            // Yükleme durumu
                            viewHandler.showLoading()
                        }

                        is MovieDetailUiState.Success -> {
                            // Başarı durumu - verileri göster
                            viewHandler.showContent(state)
                            // Son focus edilen view'a veya play butonuna focus ver
                            view?.post {
                                val lastFocusedView = view?.findViewById<View>(lastFocusedViewId)
                                if (lastFocusedView != null && lastFocusedView.visibility == View.VISIBLE) {
                                    lastFocusedView.requestFocus()
                                } else {
                                    viewHandler.getPlayButton().requestFocus()
                                    lastFocusedViewId = R.id.btn_play
                                }
                            }
                        }

                        is MovieDetailUiState.Error -> {
                            // Hata durumu
                            viewHandler.showError(state.message, com.pnr.tv.util.error.ErrorSeverity.MEDIUM, viewLifecycleOwner)
                        }
                    }
                }
            }
        }
    }

    /**
     * Favori durumunu gözlemler ve buton ikonunu günceller.
     * Favori durumu her zaman gösterilir (premium olsun olmasın).
     */
    private fun observeFavoriteStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isFavoriteInAnyViewer().collect { isFavorite ->
                    // Buton ikonunu güncelle - favori ise kırmızı (heart_filled), değilse boş (heart)
                    if (isFavorite) {
                        viewHandler.getAddFavoriteButton().setImageResource(R.drawable.heart_filled)
                        // İkonun içini kırmızı yap (daha belirgin görünmesi için)
                        viewHandler.getAddFavoriteButton().setColorFilter(
                            android.graphics.Color.parseColor("#FF0000"),
                            android.graphics.PorterDuff.Mode.SRC_IN,
                        )
                    } else {
                        viewHandler.getAddFavoriteButton().setImageResource(R.drawable.heart)
                        // Renk filtresini kaldır (beyaz kalır)
                        viewHandler.getAddFavoriteButton().clearColorFilter()
                    }
                }
            }
        }
    }

    /**
     * Premium durumunu gözlemler ve premium yazısını ve favori butonunun aktif/pasif durumunu günceller.
     */
    private fun observePremiumStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isPremium().collect { isPremium ->
                    // Premium değilse "(Premium)" yazısını göster, premium ise gizle
                    viewHandler.getFavoritePremiumText().visibility = if (isPremium) View.GONE else View.VISIBLE
                    // Premium değilse butonu pasif yap, premium ise aktif yap
                    viewHandler.getAddFavoriteButton().isEnabled = isPremium
                    // Pasif durumda görsel geri bildirim için alpha değeri
                    viewHandler.getAddFavoriteButton().alpha = if (isPremium) 1.0f else 0.5f
                }
            }
        }
    }

    private fun observeViewerSelectionDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.showViewerSelectionDialog.collect { viewers ->
                    // Premium kontrolü - premium değilse dialog gösterilmez
                    val isPremium = viewModel.isPremiumSync()
                    if (!isPremium) {
                        return@collect
                    }

                    SelectViewerDialog(
                        context = requireContext(),
                        viewers = viewers,
                        onViewerSelected = { viewer ->
                            viewModel.saveFavoriteForViewer(viewer)
                        },
                    ).show()
                }
            }
        }
    }
}
