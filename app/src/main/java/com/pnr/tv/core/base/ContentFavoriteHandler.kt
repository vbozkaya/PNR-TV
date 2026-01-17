package com.pnr.tv.core.base

import android.content.Context
import com.pnr.tv.R
import com.pnr.tv.repository.FavoriteRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Favori yönetimi işlemlerini yöneten handler sınıfı.
 * BaseContentViewModel'den favori yönetimi kodlarını ayırmak için oluşturulmuştur.
 *
 * @param favoriteRepository Favori işlemleri için repository
 * @param context String resource'lar için context
 */
class ContentFavoriteHandler
    @Inject
    constructor(
        private val favoriteRepository: FavoriteRepository,
        @ApplicationContext private val context: Context,
    ) {
        /**
         * İçeriği favorilere ekler.
         *
         * @param scope Coroutine scope (genellikle viewModelScope)
         * @param contentId İçerik ID'si
         * @param viewerId Viewer ID'si
         * @param onToastMessage Toast mesajı gösterileceğinde çağrılacak callback
         */
        fun addFavorite(
            scope: CoroutineScope,
            contentId: Int,
            viewerId: Int,
            onToastMessage: (String) -> Unit,
        ) {
            scope.launch {
                favoriteRepository.addFavorite(contentId, viewerId)
                onToastMessage(context.getString(R.string.toast_favorite_added))
            }
        }

        /**
         * İçeriği favorilerden çıkarır.
         *
         * @param scope Coroutine scope (genellikle viewModelScope)
         * @param contentId İçerik ID'si
         * @param viewerId Viewer ID'si
         * @param onToastMessage Toast mesajı gösterileceğinde çağrılacak callback
         */
        fun removeFavorite(
            scope: CoroutineScope,
            contentId: Int,
            viewerId: Int,
            onToastMessage: (String) -> Unit,
        ) {
            scope.launch {
                favoriteRepository.removeFavorite(contentId, viewerId)
                onToastMessage(context.getString(R.string.toast_favorite_removed))
            }
        }

        /**
         * İçeriğin favori olup olmadığını kontrol eder.
         *
         * @param contentId İçerik ID'si
         * @param viewerId Viewer ID'si
         * @return Flow<Boolean> - Favori durumu
         */
        fun isFavorite(
            contentId: Int,
            viewerId: Int,
        ): Flow<Boolean> = favoriteRepository.isFavorite(contentId, viewerId)
    }
