package com.pnr.tv.repository

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import com.pnr.tv.Constants
import com.pnr.tv.db.dao.LiveStreamCategoryDao
import com.pnr.tv.db.dao.LiveStreamDao
import com.pnr.tv.db.entity.LiveStreamCategoryEntity
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.di.IptvRetrofit
import com.pnr.tv.network.ApiActions
import com.pnr.tv.network.dto.toEntity
import com.pnr.tv.repository.Result.Success
import com.pnr.tv.repository.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
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

        suspend fun refreshLiveStreams(): Result<Unit> =
            safeApiCall { api, user, pass ->
                Timber.d("═══════════════════════════════════════")
                Timber.d("📡 CANLI YAYIN VERİLERİ GÜNCELENİYOR...")
                Timber.d("═══════════════════════════════════════")
                
                refreshLiveStreamCategories()
                
                val liveStreamsDto = api.getLiveStreams(user, pass, ApiActions.GET_LIVE_STREAMS)
                Timber.d("✅ API'den ${liveStreamsDto.size} canlı yayın alındı")
                
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
                
                liveStreamDao.replaceAll(entities)
                Timber.d("💾 ${entities.size} canlı yayın veritabanına kaydedildi")
                Timber.d("═══════════════════════════════════════")
            }

        suspend fun refreshLiveStreamCategories(): Result<Unit> =
            safeApiCall { api, user, pass ->
                val categoriesDto = api.getLiveStreamCategories(user, pass, ApiActions.GET_LIVE_CATEGORIES)
                val entities = categoriesDto.mapIndexedNotNull { index, dto -> dto.toEntity(sortOrder = index) }
                liveStreamCategoryDao.replaceAll(entities)
            }

        // ==================== Image Preloading ====================

        suspend fun preloadAllLiveStreamIcons(): Result<Unit> {
            return try {
                val allChannels = liveStreamDao.getAll().firstOrNull() ?: emptyList()
                allChannels.forEach { channel ->
                    if (!channel.streamIconUrl.isNullOrBlank()) {
                        val request = ImageRequest.Builder(context).data(channel.streamIconUrl).target(null).build()
                        context.imageLoader.enqueue(request)
                    }
                }
                Success(Unit)
            } catch (e: Exception) {
                com.pnr.tv.util.ErrorHelper.createImagePreloadError(e, context)
            }
        }
    }

