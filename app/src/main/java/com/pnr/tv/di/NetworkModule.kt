package com.pnr.tv.di

import com.pnr.tv.BuildConfig
import com.pnr.tv.Constants
import com.pnr.tv.network.RateLimiterInterceptor
import com.pnr.tv.network.TmdbApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for IPTV Retrofit
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IptvRetrofit

/**
 * Qualifier for TMDB Retrofit
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TmdbRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            // Debug build'de BODY, Release'de NONE
            level =
                if (BuildConfig.ENABLE_LOGGING) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
        }
    }

    @Provides
    @Singleton
    fun provideRateLimiterInterceptor(): RateLimiterInterceptor {
        // IPTV sunucu yükünü azaltmak için ardışık istekler arasında 500ms gecikme
        return RateLimiterInterceptor(minDelayMs = 500L)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        rateLimiterInterceptor: RateLimiterInterceptor,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(rateLimiterInterceptor) // Rate limiter önce eklenir
            .addInterceptor(loggingInterceptor) // Logging sonra eklenir
            // Timeout değerleri - IPTV stream'leri için yüksek tutulmuştur
            .connectTimeout(Constants.Network.TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .readTimeout(Constants.Network.TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .writeTimeout(Constants.Network.TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            // Otomatik retry için
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    /**
     * Dinamik baseUrl kullanımı için temel Retrofit.Builder.
     * Repository katmanı, kullanıcı DNS'ine göre baseUrl ekleyerek gerçek Retrofit/ApiService üretir.
     */
    @Provides
    @Singleton
    @IptvRetrofit
    fun provideRetrofitBuilder(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): Retrofit.Builder {
        return Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
    }

    /**
     * TMDB API için Retrofit instance.
     */
    @Provides
    @Singleton
    @TmdbRetrofit
    fun provideTmdbRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.Tmdb.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    /**
     * TMDB API servisi.
     */
    @Provides
    @Singleton
    fun provideTmdbApiService(
        @TmdbRetrofit retrofit: Retrofit,
    ): TmdbApiService {
        return retrofit.create(TmdbApiService::class.java)
    }
}
