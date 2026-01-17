package com.pnr.tv.ui.series

import com.pnr.tv.db.entity.ViewerEntity
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.repository.ViewerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Dizi favori yönetimi işlemlerini yöneten sınıf.
 * Favori ekleme, çıkarma ve kontrol işlemlerini gerçekleştirir.
 */
class SeriesFavoriteHandler
    @Inject
    constructor(
        private val contentRepository: ContentRepository,
        private val viewerRepository: ViewerRepository,
    ) {
        /**
         * Favorilere ekleme için izleyici seçim dialog'unu gösterir.
         *
         * @param coroutineScope Coroutine scope for launching async operations
         * @param onShowViewerSelectionDialog Callback to show viewer selection dialog
         */
        fun addToFavorites(
            coroutineScope: CoroutineScope,
            onShowViewerSelectionDialog: suspend (List<ViewerEntity>) -> Unit,
        ) {
            coroutineScope.launch {
                val viewers = viewerRepository.getAllViewers().firstOrNull() ?: emptyList()
                if (viewers.isNotEmpty()) {
                    onShowViewerSelectionDialog(viewers)
                } else {
                    // İzleyici yoksa hata log'u (UI'da gösterilmez, sadece log)
                    Timber.w("Favori eklenemedi: İzleyici bulunamadı")
                }
            }
        }

        /**
         * Seçilen izleyici için diziyi favorilere ekler.
         *
         * @param streamId Dizi stream ID'si
         * @param viewer İzleyici bilgisi
         * @param coroutineScope Coroutine scope for launching async operations
         */
        fun saveFavoriteForViewer(
            streamId: Int,
            viewer: ViewerEntity,
            coroutineScope: CoroutineScope,
        ) {
            coroutineScope.launch {
                contentRepository.addFavorite(streamId, viewer.id)
            }
        }

        /**
         * Dizinin belirli bir izleyici için favori olup olmadığını kontrol eder.
         *
         * @param streamId Dizi stream ID'si
         * @param viewerId İzleyici ID'si
         * @return Favori durumunu dönen Flow
         */
        fun isFavorite(streamId: Int, viewerId: Int): Flow<Boolean> {
            return contentRepository.isFavorite(streamId, viewerId)
        }

        /**
         * Diziyi belirli bir izleyici için favorilerden çıkarır.
         *
         * @param streamId Dizi stream ID'si
         * @param viewer İzleyici bilgisi
         * @param coroutineScope Coroutine scope for launching async operations
         */
        fun removeFavoriteForViewer(
            streamId: Int,
            viewer: ViewerEntity,
            coroutineScope: CoroutineScope,
        ) {
            coroutineScope.launch {
                contentRepository.removeFavorite(streamId, viewer.id)
            }
        }

        /**
         * Diziyi tüm izleyicilerden favorilerden çıkarır.
         * Toggle favori işlemi için kullanılır - tüm izleyicilerden favoriyi kaldırır.
         *
         * @param streamId Dizi stream ID'si
         * @param coroutineScope Coroutine scope for launching async operations
         */
        fun removeFavoriteForAnyViewer(
            streamId: Int,
            coroutineScope: CoroutineScope,
        ) {
            coroutineScope.launch {
                contentRepository.removeFavoriteForAllViewers(streamId)
            }
        }

        /**
         * Dizinin herhangi bir izleyici için favori olup olmadığını kontrol eder.
         * Tüm izleyiciler için favori durumunu kontrol eder ve en az birinde favori ise true döner.
         *
         * @param streamId Dizi stream ID'si
         * @return Herhangi bir izleyici için favori durumunu dönen Flow
         */
        fun isFavoriteInAnyViewer(streamId: Int): Flow<Boolean> {
            return viewerRepository.getAllViewers().flatMapLatest { viewers ->
                if (viewers.isEmpty()) {
                    flowOf(false)
                } else {
                    // Her izleyici için favori durumunu kontrol et ve birleştir
                    combine(
                        viewers.map { viewer ->
                            contentRepository.isFavorite(streamId, viewer.id)
                        },
                    ) { favoriteStates ->
                        favoriteStates.any { it }
                    }
                }
            }
        }
    }
