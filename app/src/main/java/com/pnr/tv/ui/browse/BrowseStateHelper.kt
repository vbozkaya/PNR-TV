package com.pnr.tv.ui.browse

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pnr.tv.R
import com.pnr.tv.model.ContentItem
import com.pnr.tv.model.ContentType
import com.pnr.tv.ui.movies.MovieViewModel
import com.pnr.tv.ui.series.SeriesViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * İçerik tarama sayfasındaki hata, yükleme ve boş sayfa (empty state) yönetimini koordine eden helper sınıfı.
 *
 * @param lifecycleOwner LifecycleOwner (genellikle viewLifecycleOwner) - coroutine scope için gerekli
 * @param contentType İçerik türü (MOVIES, SERIES, LIVE_TV)
 * @param movieViewModel Filmler için ViewModel
 * @param seriesViewModel Diziler için ViewModel
 * @param stateCallbacks UI state güncellemelerini yapmak için callback interface
 */
class BrowseStateHelper(
    private val lifecycleOwner: LifecycleOwner,
    private val contentType: ContentType?,
    private val movieViewModel: MovieViewModel,
    private val seriesViewModel: SeriesViewModel,
    private val stateCallbacks: StateCallbacks,
) {
    /**
     * UI state güncellemelerini yapmak için callback interface.
     */
    interface StateCallbacks {
        /**
         * Hata durumunu gösterir.
         */
        fun showErrorState(message: String)

        /**
         * Yükleme durumunu gösterir.
         */
        fun showLoadingState()

        /**
         * Normal içerik durumunu gösterir.
         */
        fun showContentState()

        /**
         * Boş durum mesajını gösterir.
         */
        fun showEmptyState(message: String)

        /**
         * String resource'u alır.
         */
        fun getString(resId: Int): String
    }

    /**
     * Hata durumunu gözlemler ve UI'ı günceller.
     */
    fun observeErrorState() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val errorFlow =
                    when (contentType) {
                        ContentType.MOVIES -> movieViewModel.moviesErrorMessage
                        ContentType.SERIES -> seriesViewModel.seriesErrorMessage
                        else -> kotlinx.coroutines.flow.flowOf(null)
                    }
                errorFlow.collect { errorMsg ->
                    errorMsg?.let {
                        stateCallbacks.showErrorState(it)
                    }
                }
            }
        }
    }

    /**
     * Yükleme durumunu gözlemler ve UI'ı günceller.
     */
    fun observeLoadingState() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val loadingFlow =
                    when (contentType) {
                        ContentType.MOVIES -> movieViewModel.isMoviesLoading
                        ContentType.SERIES -> seriesViewModel.isSeriesLoading
                        else -> kotlinx.coroutines.flow.flowOf(false)
                    }
                val errorFlow =
                    when (contentType) {
                        ContentType.MOVIES -> movieViewModel.moviesErrorMessage
                        ContentType.SERIES -> seriesViewModel.seriesErrorMessage
                        else -> kotlinx.coroutines.flow.flowOf(null)
                    }
                loadingFlow.collect { isLoading ->
                    if (isLoading) {
                        stateCallbacks.showLoadingState()
                    } else {
                        val hasError = errorFlow.firstOrNull() != null
                        if (!hasError) {
                            stateCallbacks.showContentState()
                        }
                    }
                }
            }
        }
    }

    /**
     * Boş sayfa durumunu günceller.
     * Favoriler kategorisi için özel mesajlar belirler.
     *
     * @param contents İçerik listesi
     * @param selectedCategoryId Seçili kategori ID'si
     */
    fun updateEmptyState(
        contents: List<ContentItem>,
        selectedCategoryId: String?,
    ) {
        if (contents.isEmpty()) {
            // Favorites kategorisi için özel mesajlar
            val isFavoritesCategory =
                when (contentType) {
                    ContentType.MOVIES -> {
                        selectedCategoryId == MovieViewModel.VIRTUAL_CATEGORY_ID_FAVORITES_MOVIES
                    }
                    ContentType.SERIES -> {
                        selectedCategoryId == SeriesViewModel.VIRTUAL_CATEGORY_ID_FAVORITES_SERIES
                    }
                    else -> false
                }

            val message =
                when {
                    isFavoritesCategory && contentType == ContentType.MOVIES -> {
                        stateCallbacks.getString(R.string.empty_movies_favorites)
                    }
                    isFavoritesCategory && contentType == ContentType.SERIES -> {
                        stateCallbacks.getString(R.string.empty_series_favorites)
                    }
                    contentType == ContentType.MOVIES -> {
                        stateCallbacks.getString(R.string.empty_movies)
                    }
                    contentType == ContentType.SERIES -> {
                        stateCallbacks.getString(R.string.empty_series)
                    }
                    contentType == ContentType.LIVE_TV -> {
                        stateCallbacks.getString(R.string.empty_live_streams)
                    }
                    else -> {
                        stateCallbacks.getString(R.string.empty_category_content)
                    }
                }

            stateCallbacks.showEmptyState(message)
        } else {
            stateCallbacks.showContentState()
        }
    }
}
