package com.pnr.tv.util

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Scale
import coil.size.Size
import com.pnr.tv.R
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Tüm uygulama için merkezi arkaplan yönetimi sağlar.
 * Görseli bir kez yükleyip cache'ler, böylece bellek kullanımını minimize eder.
 *
 * Özellikler:
 * - Singleton pattern ile tek instance
 * - Thread-safe yükleme
 * - Ekran boyutuna göre optimize edilmiş sizing
 * - Low memory durumlarında güvenli fallback
 * - Coil cache mekanizmasını kullanır
 */
object BackgroundManager {
    private const val TAG = "BACKGROUND"
    private var cachedBackground: Drawable? = null
    private val loadMutex = Mutex()
    private var isLoaded = false

    /**
     * Arkaplan görselini yükler veya cache'den döndürür.
     * Thread-safe ve bellek dostu bir şekilde çalışır.
     *
     * @param context Context (Activity veya Application)
     * @param imageLoader Coil ImageLoader instance'ı
     * @param onSuccess Yükleme başarılı olduğunda çağrılır (opsiyonel)
     * @param onError Yükleme başarısız olduğunda çağrılır (opsiyonel)
     */
    suspend fun loadBackground(
        context: Context,
        imageLoader: ImageLoader,
        onSuccess: ((Drawable) -> Unit)? = null,
        onError: (() -> Unit)? = null,
    ) {
        Timber.tag(TAG).d("🔄 loadBackground() çağrıldı - Context: ${context.javaClass.simpleName}")

        // Eğer zaten yüklenmişse, cache'den döndür
        if (isLoaded && cachedBackground != null) {
            Timber.tag(TAG).d("✅ Cache'den döndürülüyor (zaten yüklenmiş)")
            onSuccess?.invoke(cachedBackground!!)
            return
        }

        Timber.tag(TAG).d("⏳ Thread-safe yükleme başlıyor...")

        // Thread-safe yükleme
        loadMutex.withLock {
            // Double-check: başka bir thread yüklediyse tekrar kontrol et
            if (isLoaded && cachedBackground != null) {
                Timber.tag(TAG).d("✅ Mutex içinde cache kontrolü - Cache'den döndürülüyor")
                onSuccess?.invoke(cachedBackground!!)
                return@withLock
            }

            try {
                // Ekran boyutuna göre optimize edilmiş boyut hesapla
                val screenSize = getScreenSize(context)
                // Minimum boyut kontrolü (çok küçük ekranlar için)
                val minWidth = 480
                val minHeight = 320
                // Maksimum boyut kontrolü (çok büyük ekranlar için)
                val maxWidth = screenSize.first.coerceIn(minWidth, 1920) // 480-1920px arası
                val maxHeight = screenSize.second.coerceIn(minHeight, 1080) // 320-1080px arası

                Timber.tag(TAG).d("📱 Ekran boyutu: ${screenSize.first}x${screenSize.second}, Optimize: ${maxWidth}x$maxHeight")
                Timber.tag(TAG).d("📥 ImageRequest oluşturuluyor...")

                val request =
                    ImageRequest.Builder(context)
                        .data(R.drawable.home_background)
                        .size(Size(maxWidth, maxHeight)) // Ekran boyutuna göre optimize
                        .scale(Scale.FILL)
                        .allowHardware(true) // Hardware acceleration - arkaplan için de etkin
                        .allowRgb565(true) // RGB565 formatı (daha az bellek)
                        .memoryCacheKey("app_background") // Cache key - aynı görsel her zaman cache'den gelir
                        .build()

                Timber.tag(TAG).d("⏳ ImageLoader.execute() çağrılıyor...")
                val drawable = imageLoader.execute(request).drawable

                if (drawable != null) {
                    cachedBackground = drawable
                    isLoaded = true
                    Timber.tag(TAG).d("✅ Arkaplan başarıyla yüklendi ve cache'lendi - Drawable: ${drawable.javaClass.simpleName}")
                    onSuccess?.invoke(drawable)
                } else {
                    Timber.tag(TAG).w("⚠️ Arkaplan drawable null döndü, fallback kullanılacak")
                    onError?.invoke()
                }
            } catch (e: OutOfMemoryError) {
                Timber.tag(TAG).e(e, "❌ Bellek hatası - Arkaplan yüklenemedi")
                // Low memory durumunda cache'i temizle
                clearCache()
                onError?.invoke()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "❌ Arkaplan yükleme hatası: ${e.message}")
                onError?.invoke()
            }
        }
    }

    /**
     * Cache'lenmiş arkaplanı döndürür.
     * Eğer yüklenmemişse null döner.
     */
    fun getCachedBackground(): Drawable? {
        val result = cachedBackground
        Timber.tag(TAG).d("📦 getCachedBackground() - Cache durumu: ${if (result != null) "VAR" else "YOK"}, isLoaded: $isLoaded")
        return result
    }

    /**
     * Cache'i temizler. Low memory durumlarında çağrılabilir.
     */
    fun clearCache() {
        cachedBackground = null
        isLoaded = false
        Timber.tag(TAG).d("🗑️ Arkaplan cache temizlendi")
    }

    /**
     * Ekran boyutunu piksel cinsinden döndürür.
     */
    private fun getScreenSize(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val displayMetrics = DisplayMetrics()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val windowMetrics = windowManager?.currentWindowMetrics
            val bounds = windowMetrics?.bounds
            if (bounds != null) {
                return Pair(bounds.width(), bounds.height())
            }
        }

        // Fallback for older Android versions
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getMetrics(displayMetrics)

        return Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    /**
     * Fallback arkaplan (renk) döndürür.
     * Görsel yüklenemediğinde kullanılır.
     */
    fun getFallbackBackground(context: Context): Drawable? {
        return try {
            Timber.tag(TAG).d("🔄 Fallback arkaplan yükleniyor...")
            val fallback = ContextCompat.getDrawable(context, R.drawable.home_background)
            if (fallback != null) {
                Timber.tag(TAG).d("✅ Fallback arkaplan başarıyla yüklendi")
            } else {
                Timber.tag(TAG).w("⚠️ Fallback arkaplan null döndü")
            }
            fallback
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Fallback arkaplan yüklenemedi: ${e.message}")
            null
        }
    }
}
