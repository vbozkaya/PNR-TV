package com.pnr.tv.di

import android.content.Context
import com.pnr.tv.BuildConfig
import com.pnr.tv.core.constants.NetworkConstants
import com.pnr.tv.network.RateLimiterInterceptor
import com.pnr.tv.network.TmdbApiService
import com.pnr.tv.security.CertificatePinningConfig
import com.pnr.tv.security.KeystoreManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
            // Debug build'de BASIC (header'lar ve status code), Release'de NONE (güvenlik ve performans için)
            // Production'da logging yapılmaz - güvenlik ve performans için
            level =
                if (BuildConfig.IS_PRODUCTION) {
                    HttpLoggingInterceptor.Level.NONE
                } else {
                    HttpLoggingInterceptor.Level.BASIC
                }
        }
    }

    @Provides
    @Singleton
    fun provideRateLimiterInterceptor(): RateLimiterInterceptor {
        // IPTV sunucu yükünü azaltmak için ardışık istekler arasında gecikme
        return RateLimiterInterceptor(minDelayMs = NetworkConstants.Network.RATE_LIMITER_MIN_DELAY_MS)
    }

    /**
     * Certificate Pinner sağlar.
     *
     * Production build'de certificate pinning aktif, debug build'de devre dışı.
     * Bu, MITM (Man-in-the-Middle) saldırılarına karşı koruma sağlar.
     *
     * Certificate pin'leri [CertificatePinningConfig]'den alınır.
     * Gerçek pin'leri almak için [CertificatePinningConfig] dosyasındaki talimatları takip edin.
     */
    @Provides
    @Singleton
    fun provideCertificatePinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()

        // Certificate pin'lerini config'den al
        val pins = CertificatePinningConfig.getCertificatePins()

        // Her host için pin'leri ekle
        pins.forEach { (host, pinList) ->
            pinList.forEach { pin ->
                builder.add(host, pin)
            }
        }

        return builder.build()
    }

    /**
     * Unsafe SSL Trust Manager - Tüm sertifikaları kabul eder.
     * IPTV sunucularının çoğu geçersiz veya self-signed sertifikalara sahip olduğu için gerekli.
     *
     * UYARI: Bu güvenlik riski oluşturur, sadece IPTV sunucuları için kullanılmalıdır.
     */
    @Provides
    @Singleton
    fun provideUnsafeTrustManager(): X509TrustManager {
        return object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) {
                // Tüm client sertifikalarını kabul et
            }

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) {
                // Tüm server sertifikalarını kabul et
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }
        }
    }

    /**
     * Unsafe SSL Context - Tüm sertifikaları kabul eden SSL context.
     */
    @Provides
    @Singleton
    fun provideUnsafeSslContext(trustManager: X509TrustManager): SSLContext {
        return try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            sslContext
        } catch (e: Exception) {
            Timber.e(e, "Unsafe SSL Context oluşturulamadı")
            throw RuntimeException("Unsafe SSL Context oluşturulamadı", e)
        }
    }

    /**
     * IPTV API'ler için OkHttpClient.
     * Certificate Pinning YOK - IPTV sunucuları kullanıcı DNS'ine göre değiştiği için.
     * Unsafe SSL desteği VAR - Geçersiz/self-signed sertifikalara sahip sunucular için.
     */
    @Provides
    @Singleton
    @IptvRetrofit
    fun provideIptvOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        rateLimiterInterceptor: RateLimiterInterceptor,
        sslContext: SSLContext,
        trustManager: X509TrustManager,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(rateLimiterInterceptor) // Rate limiter önce eklenir
            .addInterceptor(loggingInterceptor) // Logging sonra eklenir
            // Unsafe SSL desteği - IPTV sunucularının geçersiz sertifikalarını kabul et
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true } // Hostname doğrulamasını atla
            // Certificate pinning YOK - IPTV sunucuları için pin'leme yapılmıyor
            // (Kullanıcı DNS'ine göre değiştiği için)
            // Timeout değerleri - IPTV stream'leri için yüksek tutulmuştur
            .connectTimeout(NetworkConstants.Network.TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .readTimeout(NetworkConstants.Network.TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .writeTimeout(NetworkConstants.Network.TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            // Otomatik retry için
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * TMDB API için OkHttpClient.
     * Certificate Pinning VAR - TMDB API için güvenlik.
     */
    @Provides
    @Singleton
    @TmdbRetrofit
    fun provideTmdbOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        certificatePinner: CertificatePinner,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor) // Logging
            // Certificate pinning - MITM saldırılarına karşı koruma (sadece TMDB için)
            .certificatePinner(certificatePinner)
            // Timeout değerleri
            .connectTimeout(NetworkConstants.Network.TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .readTimeout(NetworkConstants.Network.TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .writeTimeout(NetworkConstants.Network.TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
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
     *
     * IPTV için özel OkHttpClient kullanır (Certificate Pinning YOK).
     */
    @Provides
    @Singleton
    @IptvRetrofit
    fun provideRetrofitBuilder(
        @IptvRetrofit iptvOkHttpClient: OkHttpClient,
        moshi: Moshi,
    ): Retrofit.Builder {
        return Retrofit.Builder()
            .client(iptvOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
    }

    /**
     * TMDB API için Retrofit instance.
     *
     * TMDB için özel OkHttpClient kullanır (Certificate Pinning VAR).
     */
    @Provides
    @Singleton
    @TmdbRetrofit
    fun provideTmdbRetrofit(
        @TmdbRetrofit tmdbOkHttpClient: OkHttpClient,
        moshi: Moshi,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(NetworkConstants.Tmdb.BASE_URL)
            .client(tmdbOkHttpClient)
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

    /**
     * TMDB API key provider - KeystoreManager kullanarak güvenli şekilde sağlar.
     */
    @Provides
    @Singleton
    @javax.inject.Named("tmdb_api_key")
    fun provideTmdbApiKey(
        @ApplicationContext context: Context,
    ): String {
        return KeystoreManager.getApiKey(context, BuildConfig.TMDB_API_KEY)
    }
}
