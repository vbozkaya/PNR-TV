package com.pnr.tv.repository

import android.content.Context
import com.pnr.tv.db.dao.LiveStreamCategoryDao
import com.pnr.tv.db.dao.LiveStreamDao
import com.pnr.tv.db.entity.LiveStreamCategoryEntity
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.di.IptvRetrofit
import com.pnr.tv.network.ApiActions
import com.pnr.tv.network.dto.toEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import retrofit2.Retrofit
import timber.log.Timber
import javax.inject.Inject

/**
 * Canlı yayınlarla ilgili tüm işlemleri yöneten repository.
 */
class LiveStreamRepository
    @Inject
    constructor(
        @IptvRetrofit retrofitBuilder: Retrofit.Builder,
        userRepository: UserRepository,
        private val liveStreamDao: LiveStreamDao,
        private val liveStreamCategoryDao: LiveStreamCategoryDao,
        @ApplicationContext context: Context,
    ) : BaseContentRepository(
            retrofitBuilder,
            userRepository,
            context,
        ) {
        // ==================== Read Operations ====================

        fun getLiveStreams(): Flow<List<LiveStreamEntity>> = liveStreamDao.getAll()

        fun getLiveStreamsByCategoryId(categoryId: Int): Flow<List<LiveStreamEntity>> = liveStreamDao.getByCategoryId(categoryId)

        fun getLiveStreamCategories(): Flow<List<LiveStreamCategoryEntity>> = liveStreamCategoryDao.getAll()

        suspend fun getLiveStreamsByIds(channelIds: List<Int>): List<LiveStreamEntity> {
            if (channelIds.isEmpty()) return emptyList()
            return liveStreamDao.getByIds(channelIds)
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
                retryDelayMs = 2000L,
                apiCall = { api, user, pass ->
                    Timber.d("═══════════════════════════════════════")
                    Timber.d("📡 CANLI YAYIN VERİLERİ GÜNCELENİYOR...")
                    Timber.d("═══════════════════════════════════════")

                    refreshLiveStreamCategories()

                    val liveStreamsDto = api.getLiveStreams(user, pass, ApiActions.GET_LIVE_STREAMS)
                    Timber.d("✅ API'den ${liveStreamsDto.size} canlı yayın alındı")

                    // Veri doğrulama - eksik field'ları kontrol et
                    val validationReport = com.pnr.tv.util.DataValidationHelper.validateLiveStreams(liveStreamsDto)
                    validationReport.logReport()

                    if (liveStreamsDto.isNotEmpty()) {
                        Timber.d("───────────────────────────────────────")
                        Timber.d("📋 İLK 3 CANLI YAYIN ÖRNEĞİ:")
                        liveStreamsDto.take(3).forEachIndexed { index, stream ->
                            Timber.d("${index + 1}. ${stream.name} (Kategori: ${stream.categoryId})")
                        }
                        Timber.d("───────────────────────────────────────")
                    }

                    val entities = liveStreamsDto.mapNotNull { it.toEntity() }
                    Timber.d("🔄 ${entities.size} canlı yayın entity'ye dönüştürüldü")

                    // Akıllı güncelleme (Upsert) kullan - sadece değişiklikleri işle
                    liveStreamDao.upsert(entities)
                    Timber.d("💾 ${entities.size} canlı yayın veritabanına akıllı güncelleme ile kaydedildi")
                    Timber.d("═══════════════════════════════════════")
                },
            )

        suspend fun refreshLiveStreamCategories(): Result<Unit> =
            safeApiCall(
                apiCall = { api, user, pass ->
                    val categoriesDto = api.getLiveStreamCategories(user, pass, ApiActions.GET_LIVE_CATEGORIES)
                    val entities = categoriesDto.mapIndexedNotNull { index, dto -> dto.toEntity(sortOrder = index) }
                    liveStreamCategoryDao.replaceAll(entities)
                },
            )
    }
