package com.pnr.tv.ui.series

import android.view.KeyEvent
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pnr.tv.R
import com.pnr.tv.extensions.loadPosterImage
import com.pnr.tv.repository.TmdbTvRepository
import com.pnr.tv.util.error.ErrorSeverity
import dagger.hilt.android.scopes.FragmentScoped
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * SeriesDetailFragment için tüm StateFlow gözlemlerini yöneten handler sınıfı.
 * Fragment'ı observer kalabalığından kurtararak sadece bir "glue code" haline getirir.
 */
@FragmentScoped
class SeriesDetailObserverHandler
    @Inject
    constructor(
        private val viewHandler: SeriesDetailViewHandler,
        private val listHandler: SeriesDetailListHandler,
        private val playbackHandler: SeriesPlaybackHandler,
        private val tmdbTvRepository: TmdbTvRepository,
        @ApplicationContext private val context: android.content.Context,
    ) {
        /**
         * Tüm observer'ları başlatır.
         * Fragment'tan tek bir çağrı ile tüm gözlemler başlatılır.
         *
         * @param fragment Fragment referansı (requireContext için)
         * @param viewModel ViewModel referansı
         * @param lifecycleOwner LifecycleOwner (viewLifecycleOwner)
         */
        fun observeAll(
            fragment: Fragment,
            viewModel: SeriesDetailViewModel,
            lifecycleOwner: LifecycleOwner,
        ) {
            observeLoadingState(viewModel, lifecycleOwner)
            observeErrorState(viewModel, lifecycleOwner)
            observeSeries(viewModel, lifecycleOwner)
            observeTmdbDetails(viewModel, lifecycleOwner)
            observeFavoriteStatus(viewModel, lifecycleOwner)
            observePremiumStatus(viewModel, lifecycleOwner)
            observeViewerSelectionDialog(fragment, viewModel, lifecycleOwner)
        }

        /**
         * Loading state'i gözlemler ve UI'ı günceller.
         */
        private fun observeLoadingState(
            viewModel: SeriesDetailViewModel,
            lifecycleOwner: LifecycleOwner,
        ) {
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.isLoading.collect { isLoading ->
                        viewHandler.getLoadingIndicator()?.visibility = if (isLoading) View.VISIBLE else View.GONE

                        // Loading sırasında diğer bileşenleri gizle
                        if (isLoading) {
                            listHandler.getEpisodesRecyclerView()?.visibility = View.GONE
                            viewHandler.getEmptyStateContainer()?.visibility = View.GONE
                            // İlk yükleme sırasında loading container göster
                            if (viewModel.series.value == null) {
                                viewHandler.showLoading()
                            }
                        } else {
                            // Loading bittiğinde, eğer series varsa content göster
                            if (viewModel.series.value != null && viewModel.error.value.isEmpty()) {
                                viewHandler.showContent()
                            }
                        }
                    }
                }
            }
        }

        /**
         * Error state'i gözlemler ve UI'ı günceller.
         */
        private fun observeErrorState(
            viewModel: SeriesDetailViewModel,
            lifecycleOwner: LifecycleOwner,
        ) {
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.error.collect { errorMsg ->
                        if (errorMsg.isNotEmpty()) {
                            // Error container'ı göster
                            viewHandler.showError(errorMsg, ErrorSeverity.MEDIUM, lifecycleOwner)
                        }
                    }
                }
            }
        }

        /**
         * Series state'ini gözlemler ve UI'ı günceller.
         */
        private fun observeSeries(
            viewModel: SeriesDetailViewModel,
            lifecycleOwner: LifecycleOwner,
        ) {
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.series.collect { series ->
                        try {
                            series?.let {
                                // Series yüklendiğinde content göster
                                if (viewModel.error.value.isEmpty() && !viewModel.isLoading.value) {
                                    viewHandler.showContent()
                                }

                                viewHandler.getSeriesTitle()?.text = it.name ?: ""

                                // Rating - TMDB'den gelen rating'i kontrol et (observeTmdbDetails'te güncellenecek)
                                // Burada sadece IPTV rating'ini göster, TMDB rating'i observeTmdbDetails'te güncellenecek
                                viewHandler.getSeriesRating()?.let { ratingView ->
                                    if (it.rating != null && it.rating > 0) {
                                        ratingView.visibility = View.VISIBLE
                                        ratingView.text = context.getString(R.string.rating_format, it.rating)
                                    } else {
                                        ratingView.visibility = View.GONE
                                    }
                                }

                                // Poster - TMDB'den gelen poster'ı kontrol et (observeTmdbDetails'te güncellenecek)
                                // Burada sadece IPTV coverUrl'ini göster, TMDB poster'ı observeTmdbDetails'te güncellenecek
                                // Her zaman maksimum limitlerle yükle (1280x720) - güvenli yaklaşım
                                viewHandler.getSeriesPoster()?.loadPosterImage(imageUrl = it.coverUrl)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "observeSeries: Error updating UI")
                        }
                    }
                }
            }
        }

        /**
         * TMDB details state'ini gözlemler ve UI'ı günceller.
         */
        private fun observeTmdbDetails(
            viewModel: SeriesDetailViewModel,
            lifecycleOwner: LifecycleOwner,
        ) {
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.tmdbDetails.collect { tmdbDetails ->
                        try {
                            val series = viewModel.series.value

                            // Poster - Önce IPTV'den, yoksa TMDB'den
                            val posterUrl = series?.coverUrl ?: tmdbTvRepository.getPosterUrlFromTv(tmdbDetails)
                            // Her zaman maksimum limitlerle yükle (1280x720) - güvenli yaklaşım
                            viewHandler.getSeriesPoster()?.loadPosterImage(imageUrl = posterUrl)

                            // Rating - Önce IPTV'den, yoksa TMDB'den
                            val rating = series?.rating ?: tmdbTvRepository.getRatingFromTv(tmdbDetails)
                            viewHandler.getSeriesRating()?.let { ratingView ->
                                if (rating != null && rating > 0) {
                                    ratingView.visibility = View.VISIBLE
                                    ratingView.text = context.getString(R.string.rating_format, rating)
                                } else {
                                    ratingView.visibility = View.GONE
                                }
                            }

                            val creator = viewModel.getCreator()
                            viewHandler.getCreatorLayout()?.visibility =
                                if (!creator.isNullOrBlank()) {
                                    viewHandler.getSeriesCreator()?.text = creator
                                    View.VISIBLE
                                } else {
                                    View.GONE
                                }

                            val genre = viewModel.getGenre()
                            viewHandler.getGenreLayout()?.visibility =
                                if (!genre.isNullOrBlank()) {
                                    viewHandler.getSeriesGenre()?.text = genre
                                    View.VISIBLE
                                } else {
                                    View.GONE
                                }

                            val cast = viewModel.getCast()
                            viewHandler.getCastLayout()?.visibility =
                                if (!cast.isNullOrBlank()) {
                                    viewHandler.getSeriesCast()?.text = cast
                                    View.VISIBLE
                                } else {
                                    View.GONE
                                }

                            val overview = viewModel.getOverview()
                            viewHandler.getSeriesPlot()?.text = overview?.takeIf { it.isNotBlank() } ?: context.getString(R.string.no_overview)
                        } catch (e: Exception) {
                            Timber.e(e, "observeTmdbDetails: Error updating UI")
                        }
                    }
                }
            }
        }

        /**
         * Favori durumunu gözlemler ve buton ikonunu günceller.
         * Favori durumu her zaman gösterilir (premium olsun olmasın).
         */
        private fun observeFavoriteStatus(
            viewModel: SeriesDetailViewModel,
            lifecycleOwner: LifecycleOwner,
        ) {
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.isFavoriteInAnyViewer().collect { isFavorite ->
                        // Buton ikonunu güncelle - favori ise kırmızı (heart_filled), değilse boş (heart)
                        viewHandler.getAddFavoriteButton()?.let { button ->
                            if (isFavorite) {
                                button.setImageResource(R.drawable.heart_filled)
                                // İkonun içini kırmızı yap (daha belirgin görünmesi için)
                                button.setColorFilter(
                                    android.graphics.Color.parseColor("#FF0000"),
                                    android.graphics.PorterDuff.Mode.SRC_IN,
                                )
                            } else {
                                button.setImageResource(R.drawable.heart)
                                // Renk filtresini kaldır (beyaz kalır)
                                button.clearColorFilter()
                            }
                        }
                    }
                }
            }
        }

        /**
         * Premium durumunu gözlemler ve premium yazısını ve favori butonunun aktif/pasif durumunu günceller.
         */
        private fun observePremiumStatus(
            viewModel: SeriesDetailViewModel,
            lifecycleOwner: LifecycleOwner,
        ) {
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.isPremium().collect { isPremium ->
                        // Premium değilse "(Premium)" yazısını göster, premium ise gizle
                        viewHandler.getFavoritePremiumText()?.visibility = if (isPremium) View.GONE else View.VISIBLE
                        // Premium değilse butonu pasif yap, premium ise aktif yap
                        viewHandler.getAddFavoriteButton()?.let { button ->
                            button.isEnabled = isPremium
                            // Pasif durumda görsel geri bildirim için alpha değeri
                            button.alpha = if (isPremium) 1.0f else 0.5f
                        }
                    }
                }
            }
        }

        /**
         * İzleyici seçim dialog'unu gözlemler ve gösterir.
         */
        private fun observeViewerSelectionDialog(
            fragment: Fragment,
            viewModel: SeriesDetailViewModel,
            lifecycleOwner: LifecycleOwner,
        ) {
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.showViewerSelectionDialog.collect { viewers ->
                        // Premium kontrolü - premium değilse dialog gösterilmez
                        val isPremium = viewModel.isPremiumSync()
                        if (!isPremium) {
                            return@collect
                        }

                        com.pnr.tv.ui.viewers.SelectViewerDialog(
                            context = fragment.requireContext(),
                            viewers = viewers,
                            onViewerSelected = { viewer ->
                                viewModel.saveFavoriteForViewer(viewer)
                            },
                        ).show()
                    }
                }
            }
        }

        /**
         * Favori butonunu kurar ve listener'ları ayarlar.
         *
         * @param fragment Fragment referansı (lifecycleScope için)
         * @param viewModel ViewModel referansı
         */
        fun setupFavoriteButton(
            fragment: Fragment,
            viewModel: SeriesDetailViewModel,
        ) {
            val favoriteButton = viewHandler.getAddFavoriteButton()
            if (favoriteButton == null) {
                return
            }

            favoriteButton.setOnClickListener {
                // Toggle favori - eğer herhangi bir izleyici için favori ise çıkar, değilse ekle
                fragment.viewLifecycleOwner.lifecycleScope.launch {
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

            // Favoriler butonunun yönlendirmeleri
            favoriteButton.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            // Aşağı tuşu: Seçili sekmeye odaklan
                            listHandler.getSeasonTabLayout()?.let { seasonTabLayout ->
                                val selectedTab = seasonTabLayout.getTabAt(seasonTabLayout.selectedTabPosition)
                                if (selectedTab != null && seasonTabLayout.tabCount > 0) {
                                    selectedTab.customView?.requestFocus() ?: seasonTabLayout.getTabAt(0)?.customView?.requestFocus()
                                    return@setOnKeyListener true
                                }
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            // Sol ve sağ tuşları: Olayı tüket
                            return@setOnKeyListener true
                        }
                    }
                }
                false
            }
        }
    }
