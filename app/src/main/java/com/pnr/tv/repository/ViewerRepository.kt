package com.pnr.tv.repository

import com.pnr.tv.ui.main.SessionManager
import com.pnr.tv.db.dao.ViewerDao
import com.pnr.tv.db.entity.ViewerEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class ViewerRepository
    @Inject
    constructor(
        private val viewerDao: ViewerDao,
        private val sessionManager: SessionManager,
    ) {
        suspend fun addViewer(viewer: ViewerEntity): Long {
            val userId = sessionManager.getCurrentUserId().firstOrNull()
            return if (userId != null) {
                viewerDao.insert(viewer.copy(userId = userId))
            } else {
                0L // Kullanıcı yoksa viewer eklenemez
            }
        }

        suspend fun deleteViewer(viewer: ViewerEntity) {
            viewerDao.delete(viewer.id)
        }

        fun getAllViewers(): Flow<List<ViewerEntity>> =
            sessionManager.getCurrentUserId().flatMapLatest { userId ->
                if (userId == null) {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                } else {
                    viewerDao.getAllViewers(userId)
                }
            }

        suspend fun getViewerById(id: Int): ViewerEntity? {
            val userId = sessionManager.getCurrentUserId().firstOrNull() ?: return null
            return viewerDao.getViewerById(id, userId)
        }

        fun getViewerIdsWithFavorites(): Flow<List<Int>> =
            sessionManager.getCurrentUserId().flatMapLatest { userId ->
                if (userId == null) {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                } else {
                    viewerDao.getViewerIdsWithFavorites(userId)
                }
            }
    }
