package com.pnr.tv

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pnr.tv.BuildConfig
import com.pnr.tv.util.LocaleHelper
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PnrTvApplication : Application(), ImageLoaderFactory, Configuration.Provider {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
    }
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    override fun onCreate() {
        super.onCreate()
        initializeCrashlytics()
        setupTimber()
        initializeLeakCanary()
    }

    /**
     * Uygulama boyunca kullanılacak merkezi ImageLoader'ı oluşturur.
     * İstek sayısı limitlendirilmiş (maksimum 1) bir ImageLoader sağlar.
     * Düşük limit, sunucunun istekleri reddetmesini önler ve daha güvenilir indirme sağlar.
     */
    override fun newImageLoader(): ImageLoader {
        // Özel bir Dispatcher oluştur - aynı anda maksimum 1 istek
        val dispatcher =
            Dispatcher().apply {
                maxRequests = 1
                maxRequestsPerHost = 1
            }

        // Özel OkHttpClient oluştur - limitli dispatcher ile
        val okHttpClient =
            OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .build()

        // ImageLoader'ı özel OkHttpClient ile oluştur
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .build()
    }

    /**
     * Firebase Crashlytics'i başlatır ve yapılandırır
     */
    private fun initializeCrashlytics() {
        val crashlytics = FirebaseCrashlytics.getInstance()
        
        val isDebug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        
        // Custom key'ler ekle - her crash'te bu bilgiler gönderilecek
        try {
            crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
            crashlytics.setCustomKey("version_code", BuildConfig.VERSION_CODE)
            crashlytics.setCustomKey("build_type", if (isDebug) "debug" else "release")
            crashlytics.setCustomKey("package_name", packageName)
        } catch (e: Exception) {
            Timber.e(e, "Crashlytics custom key'ler ayarlanırken hata oluştu")
        }
        
        // Önceki default handler'ı sakla
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        // Uncaught exception handler ekle - yakalanmayan tüm exception'ları Firebase'e gönder
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                // Önce Crashlytics'e gönder
                crashlytics.recordException(exception)
                
                // Custom log ekle
                crashlytics.log("Uncaught exception in thread: ${thread.name}")
                crashlytics.setCustomKey("crash_thread_name", thread.name)
                
                // Crashlytics'in exception'ı göndermesi için bir süre bekle
                Thread.sleep(2000)
            } catch (e: Exception) {
                // Crashlytics'e gönderirken hata olursa en azından log'la
                android.util.Log.e("Crashlytics", "Exception gönderilemedi", e)
            }
            
            // Önceki handler'ı çağır (genellikle sistem default handler)
            defaultHandler?.uncaughtException(thread, exception)
        }
        
        if (isDebug) {
            Timber.d("🔥 Crashlytics başlatıldı (DEBUG mod) - Tüm çökmeler Firebase'e gönderilecek")
        } else {
            Timber.d("🔥 Crashlytics başlatıldı (PRODUCTION mod) - Tüm çökmeler Firebase'e gönderilecek")
        }
    }

    /**
     * Timber logging'i yapılandırır
     * Production'da Crashlytics ile entegre edilmiştir
     */
    private fun setupTimber() {
        val isDebug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        } else {
            // Production için Crashlytics entegre edilmiş Timber Tree
            Timber.plant(CrashlyticsTree())
        }
    }

    /**
     * LeakCanary'yi başlatır (sadece debug build'de)
     * Memory leak'leri otomatik olarak tespit eder ve bildirir
     * 
     * Not: LeakCanary otomatik olarak başlatılır, sadece debug build'de çalışır
     * debugImplementation ile eklendiği için release build'de class bulunamaz
     */
    private fun initializeLeakCanary() {
        if (BuildConfig.DEBUG) {
            Timber.d("🔍 LeakCanary aktif - Memory leak'ler otomatik tespit edilecek")
        }
    }

    /**
     * Timber Tree implementation that sends logs to Firebase Crashlytics
     */
    private class CrashlyticsTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            val crashlytics = FirebaseCrashlytics.getInstance()
            
            // Sadece warning ve error seviyesindeki logları Crashlytics'e gönder
            if (priority >= android.util.Log.WARN) {
                // Log mesajını Crashlytics'e gönder
                crashlytics.log("[$tag] $message")
                
                // Exception varsa Crashlytics'e gönder
                if (t != null) {
                    crashlytics.recordException(t)
                } else if (priority >= android.util.Log.ERROR) {
                    // Error seviyesinde exception yoksa, mesajı exception olarak kaydet
                    crashlytics.recordException(Exception(message))
                }
            }
        }
    }

    /**
     * WorkManager konfigürasyonu
     * Hilt ile Worker dependency injection için gerekli
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    android.util.Log.DEBUG
                } else {
                    android.util.Log.ERROR
                }
            )
            .build()
}
