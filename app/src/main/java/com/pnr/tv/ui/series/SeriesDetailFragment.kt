package com.pnr.tv.ui.series

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.pnr.tv.ui.main.MainActivity
import com.pnr.tv.R
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Dizi detay sayfası için Fragment.
 * Dizi bilgilerini, sezonları ve bölümleri gösterir.
 */
@AndroidEntryPoint
class SeriesDetailFragment : Fragment() {
    private lateinit var viewModel: SeriesDetailViewModel

    @Inject
    lateinit var viewModelFactory: SeriesDetailViewModel.Factory

    @Inject
    lateinit var viewHandler: SeriesDetailViewHandler

    @Inject
    lateinit var listHandler: SeriesDetailListHandler

    @Inject
    lateinit var playbackHandler: SeriesPlaybackHandler

    @Inject
    lateinit var observerHandler: SeriesDetailObserverHandler

    private val playerActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            // Oynatıcıdan döndükten sonra yapılacak işlemler
        }

    // Back button callback - MainActivity'deyken fragment'ın kendi back tuşu yönetimi
    private var backPressedCallback: OnBackPressedCallback? = null

    companion object {
        private const val ARG_SERIES_ID = "series_id"

        fun newInstance(seriesId: Int): SeriesDetailFragment {
            return SeriesDetailFragment().apply {
                arguments =
                    Bundle().apply {
                        putInt(ARG_SERIES_ID, seriesId)
                    }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            )[SeriesDetailViewModel::class.java]

        arguments?.getInt(ARG_SERIES_ID)?.let { seriesId ->
            viewModel.loadSeries(seriesId)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_series_detail, container, false)
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
            navbarTitle = getString(R.string.page_series_details),
            onBackClicked = {
                // Activity içindeyse finish, Fragment içindeyse popBackStack
                if (activity is com.pnr.tv.ui.series.SeriesDetailActivity) {
                    requireActivity().finish()
                } else {
                    parentFragmentManager.popBackStack()
                }
            },
            onHomeClicked = {
                // Activity içindeyse finish, Fragment içindeyse popBackStack
                if (activity is com.pnr.tv.ui.series.SeriesDetailActivity) {
                    requireActivity().finish()
                } else {
                    parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                }
            },
        )
        // Retry button listener'ını ayarla
        viewHandler.setOnRetryClickListener {
            arguments?.getInt(ARG_SERIES_ID)?.let { seriesId ->
                viewModel.loadSeries(seriesId)
            }
        }
        // Playback handler'ı kur
        playbackHandler.setup(
            fragment = this,
            viewModel = viewModel,
            playerActivityLauncher = playerActivityLauncher,
        )
        // List handler'ı başlat
        listHandler.setup(
            view = view,
            onSeasonSelected = { seasonNumber -> viewModel.selectSeason(seasonNumber) },
            onEpisodeClicked = { episode -> playbackHandler.playEpisode(episode) },
            emptyStateContainer = viewHandler.getEmptyStateContainer(),
            emptyStateText = viewHandler.getEmptyStateText(),
        )
        // Sezon sekmelerinden yukarı tuşu için favori butonuna odaklanma
        listHandler.getSeasonTabLayout()?.setOnKeyListener { _, keyCode, event ->
            // Geri tuşu olayını sisteme ilet (return false)
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                return@setOnKeyListener false
            }

            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                // Yukarı tuşu: Favoriler butonuna odaklan
                viewHandler.getAddFavoriteButton()?.requestFocus()
                return@setOnKeyListener true
            }
            false
        }
        // StateFlow gözlemlerini handler'a yönlendir
        listHandler.observeSeasons(viewLifecycleOwner, viewModel.seasons)
        listHandler.observeSelectedSeasonNumber(viewLifecycleOwner, viewModel.selectedSeasonNumber)
        listHandler.observeEpisodes(
            lifecycleOwner = viewLifecycleOwner,
            episodesFlow = viewModel.episodes,
            view = view,
            hasEpisodes = { viewModel.seasons.value.isNotEmpty() },
            getEmptyStateMessage = { getString(R.string.no_episodes_found) },
        )
        // Favori butonunu kur
        observerHandler.setupFavoriteButton(this, viewModel)
        // Tüm observer'ları başlat
        observerHandler.observeAll(this, viewModel, viewLifecycleOwner)
        // Not: Resume dialog kontrolü artık PlayerActivity'de yapılıyor

        // MainActivity'deyken fragment'ın kendi back tuşu callback'ini ekle
        if (activity is MainActivity) {
            backPressedCallback =
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        // Fragment içindeyse popBackStack
                        parentFragmentManager.popBackStack()
                    }
                }
            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                backPressedCallback!!,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Activity içindeyse BaseActivity zaten navbar'ı yönetiyor
        if (activity !is com.pnr.tv.ui.series.SeriesDetailActivity) {
            (activity as? MainActivity)?.hideTopMenu()
        }

        // Handler'dan focus yönetimi
        listHandler.onResume()
    }

}
