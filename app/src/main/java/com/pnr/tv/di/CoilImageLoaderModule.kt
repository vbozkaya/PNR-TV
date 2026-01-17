package com.pnr.tv.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.pnr.tv.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Coil ImageLoader için merkezi yapılandırma modülü.
 *
 * Tüm uygulama için optimize edilmiş ImageLoader sağlar:
 * - Memory cache optimizasyonu
 * - Disk cache yapılandırması
 * - Network request limitleri
 * - Debug logging (sadece debug build'de)
 */
@Module
@InstallIn(SingletonComponent::class)
object CoilImageLoaderModule {
    /**
     * Uygulama boyunca kullanılacak merkezi ImageLoader'ı sağlar.
     *
     * Özellikler:
     * - Memory cache: Cihaz RAM'inin %25'i (maksimum)
     * - Disk cache: 50 MB
     * - Network: Maksimum 3 paralel istek, host başına 2 istek
     * - Debug logging: Sadece debug build'de aktif
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
    ): ImageLoader {
        // Özel bir Dispatcher oluştur - TV için optimize edilmiş paralel istek sayısı
        val dispatcher =
            Dispatcher().apply {
                maxRequests = 3 // TV için optimize (2-4 arası ideal)
                maxRequestsPerHost = 2 // Aynı host'tan 2 paralel istek
            }

        // Özel OkHttpClient oluştur - limitli dispatcher ile
        val okHttpClient =
            OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .build()

        // Memory cache'i sınırlandır - çok büyük görselleri önlemek için
        val memoryCache =
            MemoryCache.Builder(context)
                .maxSizePercent(0.25) // Maksimum %25 bellek kullanımı
                .build()

        // Disk cache ekle - uygulama yeniden açıldığında resimler disk'ten yüklenir
        val diskCache =
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(50 * 1024 * 1024) // 50 MB disk cache
                .build()

        // ImageLoader'ı yapılandır
        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .memoryCache(memoryCache)
            .diskCache(diskCache)
            .apply {
                // Debug build'de logging aktif et
                if (BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }
            .build()
    }
}
