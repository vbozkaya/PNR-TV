package com.pnr.tv.repository

import android.content.Context
import com.pnr.tv.core.constants.NetworkConstants
import com.pnr.tv.db.dao.LiveStreamCategoryDao
import com.pnr.tv.db.dao.LiveStreamDao
import com.pnr.tv.db.entity.LiveStreamCategoryEntity
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.di.IptvRetrofit
import com.pnr.tv.network.ApiActions
import com.pnr.tv.network.dto.toEntity
import com.pnr.tv.util.error.ErrorHelper
import com.pnr.tv.util.error.Resource
import com.pnr.tv.util.validation.DataValidationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import retrofit2.Retrofit
import timber.log.Timber
import javax.inject.Inject

/**
 * Canlı yayınlarla ilgili tüm işlemleri yöneten repository.
 */
class LiveStreamRepository
    @Inject
    constructor(
        apiServiceManager: ApiServiceManager,
        userRepository: UserRepository,
        private val liveStreamDao: LiveStreamDao,
        private val liveStreamCategoryDao: LiveStreamCategoryDao,
        @ApplicationContext context: Context,
    ) : BaseContentRepository(
            apiServiceManager,
            userRepository,
            context,
        ) {
        // ==================== Read Operations ====================

        fun getLiveStreams(): Flow<Resource<List<LiveStreamEntity>>> =
            flow {
                emit(Resource.Loading)
                try {
                    liveStreamDao.getAll().collect { data ->
                        emit(Resource.Success(data))
                    }
                } catch (e: Exception) {
                    val errorMessage = ErrorHelper.createError(e, context, errorContext = "LiveStreamRepository.getLiveStreams").message
                    emit(Resource.Error(errorMessage, e))
                }
            }

        fun getLiveStreamsByCategoryId(categoryId: Int): Flow<Resource<List<LiveStreamEntity>>> =
            flow {
                emit(Resource.Loading)
                try {
                    liveStreamDao.getByCategoryId(categoryId).collect { data ->
                        emit(Resource.Success(data))
                    }
                } catch (e: Exception) {
                    val errorMessage =
                        ErrorHelper.createError(
                            e,
                            context,
                            errorContext = "LiveStreamRepository.getLiveStreamsByCategoryId",
                        ).message
                    emit(Resource.Error(errorMessage, e))
                }
            }

        fun getLiveStreamCategories(): Flow<Resource<List<LiveStreamCategoryEntity>>> =
            flow {
                emit(Resource.Loading)
                try {
                    liveStreamCategoryDao.getAll().collect { data ->
                        emit(Resource.Success(data))
                    }
                } catch (e: Exception) {
                    val errorMessage =
                        ErrorHelper.createError(
                            e,
                            context,
                            errorContext = "LiveStreamRepository.getLiveStreamCategories",
                        ).message
                    emit(Resource.Error(errorMessage, e))
                }
            }

        suspend fun getLiveStreamsByIds(channelIds: List<Int>): List<LiveStreamEntity> {
            if (channelIds.isEmpty()) return emptyList()
            return liveStreamDao.getByIds(channelIds)
        }

        suspend fun getLiveStreamsByCategoryIdSync(categoryId: Int): List<LiveStreamEntity> {
            return liveStreamDao.getByCategoryIdSync(categoryId)
        }

        /**
         * Veritabanında canlı yayın verisi olup olmadığını kontrol eder.
         * @return true ise veri var, false ise veri yok
         */
        suspend fun hasLiveStreams(): Boolean {
            return try {
                // DAO'dan direkt veri kontrolü yap - Flow wrapper'ı kullanmadan
                val data = liveStreamDao.getAll().firstOrNull()
                data?.isNotEmpty() ?: false
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Canlı yayın veri kontrolü hatası")
                false
            }
        }

        /**
         * Veritabanında canlı yayın kategori verisi olup olmadığını kontrol eder.
         * @return true ise veri var, false ise veri yok
         */
        suspend fun hasLiveStreamCategories(): Boolean {
            return try {
                val resource = getLiveStreamCategories().firstOrNull()
                when (resource) {
                    is Resource.Success -> resource.data.isNotEmpty()
                    else -> false
                }
            } catch (e: Exception) {
                false
            }
        }

        // ==================== Refresh Operations ====================

        /**
         * Canlı yayınları yeniler
         *
         * @param forMainScreenUpdate Ana ekran güncelleme için mi? (true ise özel hata mesajları kullanılır)
         */
        suspend fun refreshLiveStreams(
            forMainScreenUpdate: Boolean = false,
            maxRetries: Int = 2,
        ): Result<Unit> =
            safeApiCall(
                forMainScreenUpdate = forMainScreenUpdate,
                maxRetries = maxRetries,
                retryDelayMs = NetworkConstants.Network.LONG_RETRY_DELAY_MILLIS,
                apiCall = { api, user, pass ->
                    refreshLiveStreamCategories()

                    val liveStreamsDto = api.getLiveStreams(user, pass, ApiActions.GET_LIVE_STREAMS)

                    // Veri doğrulama - eksik field'ları kontrol et
                    val validationReport = DataValidationHelper.validateLiveStreams(liveStreamsDto)
                    validationReport.logReport()

                    val entities = liveStreamsDto.mapNotNull { it.toEntity() }

                    // Akıllı güncelleme (Upsert) kullan - sadece değişiklikleri işle
                    liveStreamDao.upsert(entities)
                },
            )

        suspend fun refreshLiveStreamCategories(): Result<Unit> {
            return refreshCategories(
                apiCall = { api, user, pass ->
                    api.getLiveStreamCategories(user, pass, ApiActions.GET_LIVE_CATEGORIES)
                },
                entityMapper = { categoriesDto ->
                    categoriesDto.mapIndexedNotNull { index, dto ->
                        dto.toEntity(sortOrder = index)
                    }
                },
                daoClearAll = { liveStreamCategoryDao.clearAll() },
                daoInsertAll = { entities -> liveStreamCategoryDao.insertAll(entities) },
                daoGetAll = { liveStreamCategoryDao.getAll() },
            )
        }
    }
