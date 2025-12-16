package com.pnr.tv.repository

import com.pnr.tv.db.dao.ViewerDao
import com.pnr.tv.db.entity.ViewerEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ViewerRepository
    @Inject
    constructor(
        private val viewerDao: ViewerDao,
    ) {
        suspend fun addViewer(viewer: ViewerEntity): Long {
            return viewerDao.insert(viewer)
        }

        suspend fun deleteViewer(viewer: ViewerEntity) {
            viewerDao.delete(viewer.id)
        }

        fun getAllViewers(): Flow<List<ViewerEntity>> = viewerDao.getAllViewers()

        suspend fun getViewerById(id: Int): ViewerEntity? = viewerDao.getViewerById(id)

        fun getViewerIdsWithFavorites(): Flow<List<Int>> = viewerDao.getViewerIdsWithFavorites()
    }
