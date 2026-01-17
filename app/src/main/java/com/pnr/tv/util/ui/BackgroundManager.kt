package com.pnr.tv.util.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.DisplayMetrics
import android.view.WindowManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.pnr.tv.R
import com.pnr.tv.util.CrashlyticsHelper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
    // WeakReference kullanarak sistemin belleği yönetmesine izin ver
    // TV bekleme modunda sistem bitmap'leri silebilir, WeakReference ile bu durumu güvenli şekilde yönetiriz
    private var cachedBackgroundRef: WeakReference<Drawable>? = null
    private val loadMutex = Mutex()
    private var isLoaded = false

    @Volatile
    private var isCancelled = false

    /**
     * Cache'lenmiş drawable'ı güvenli bir şekilde alır.
     * WeakReference'den alır ve null/recycled kontrolü yapar.
     */
    private fun getCachedDrawable(): Drawable? {
        val ref = cachedBackgroundRef ?: return null
        val drawable = ref.get() ?: return null
        
        // WeakReference'den alınan drawable'ın hala geçerli olduğunu kontrol et
        if (isDrawableRecycled(drawable)) {
            Timber.tag(TAG).d("⚠️ WeakReference'den alınan drawable recycle edilmiş")
            cachedBackgroundRef = null
            isLoaded = false
            return null
        }
        
        return drawable
    }

    /**
     * Cache'lenmiş drawable'ı güvenli bir şekilde kaydeder.
     * WeakReference kullanarak sistemin belleği yönetmesine izin verir.
     */
    private fun setCachedDrawable(drawable: Drawable?) {
        if (drawable == null) {
            cachedBackgroundRef = null
            isLoaded = false
        } else {
            // WeakReference ile kaydet - sistem bellek yönetimi yapabilir
            cachedBackgroundRef = WeakReference(drawable)
            isLoaded = true
        }
    }

    /**
     * Arkaplan görselini Glide ile yükler veya cache'den döndürür.
     * Thread-safe ve bellek dostu bir şekilde çalışır.
     *
     * @param context Context (Activity veya Application)
     * @param imageLoader Coil ImageLoader instance'ı (artık kullanılmıyor, Glide kullanılıyor - geriye uyumluluk için bırakıldı)
     * @param onSuccess Yükleme başarılı olduğunda çağrılır (opsiyonel)
     * @param onError Yükleme başarısız olduğunda çağrılır (opsiyonel)
     */
    suspend fun loadBackground(
        context: Context,
        imageLoader: Any? = null, // Artık kullanılmıyor, Glide kullanılıyor - geriye uyumluluk için bırakıldı
        onSuccess: ((Drawable) -> Unit)? = null,
        onError: (() -> Unit)? = null,
    ) {
        // Eğer zaten yüklenmişse, cache'den döndür - ama önce recycle kontrolü yap
        val cached = getCachedBackground() // Bu metod zaten recycle kontrolü yapıyor
        if (isLoaded && cached != null) {
            onSuccess?.invoke(cached)
            return
        }

        // Thread-safe yükleme kontrolü - mutex ile double-check locking
        var shouldLoad = false
        loadMutex.withLock {
            // İptal edilmişse çık
            if (isCancelled) {
                isCancelled = false
                onError?.invoke()
                return
            }

            // Double-check: başka bir thread yüklediyse tekrar kontrol et
            // Recycle kontrolü yap - getCachedBackground zaten kontrol ediyor
            val cached = getCachedBackground()
            if (isLoaded && cached != null) {
                onSuccess?.invoke(cached)
                return
            }

            shouldLoad = true
        }

        // Eğer yüklenmesi gerekiyorsa, Glide ile yükle
        if (!shouldLoad) return

        try {
            val screenSize = getScreenSize(context)
            // Maksimum boyut limiti - 1080p (1920x1080) üzerine çıkılmasına izin verme
            // Bu, 112 MB bitmap hatasını önlemek için kritik
            val maxWidth = screenSize.first.coerceIn(480, 1920).coerceAtMost(1920) // Maksimum 1920px
            val maxHeight = screenSize.second.coerceIn(320, 1080).coerceAtMost(1080) // Maksimum 1080px

            Timber.tag(
                TAG,
            ).d(
                "📐 Arkaplan görsel yükleniyor (Glide): maxSize=${maxWidth}x$maxHeight, screenSize=${screenSize.first}x${screenSize.second}",
            )

            // Glide ile güvenli ve ölçeklendirilmiş şekilde yükle
            // RGB_565 formatı bellek tüketimini %50 azaltır
            // suspendCoroutine kullanarak callback'i suspend fonksiyona çevir
            suspendCoroutine<Unit> { continuation ->
                Glide.with(context)
                    .load(R.drawable.home_background)
                    .override(maxWidth, maxHeight) // Ekran çözünürlüğüne göre ölçeklendir (max 1920x1080)
                    .format(DecodeFormat.PREFER_RGB_565) // RGB_565 formatı - bellek kullanımını %50 azaltır
                    .diskCacheStrategy(DiskCacheStrategy.ALL) // Disk cache'i aktif et
                    .centerCrop()
                    .into(
                        object : CustomTarget<Drawable>() {
                            override fun onResourceReady(
                                resource: Drawable,
                                transition: Transition<in Drawable>?,
                            ) {
                                // Thread-safe cache kaydetme
                                kotlinx.coroutines.runBlocking {
                                    loadMutex.withLock {
                                        if (isCancelled) {
                                            isCancelled = false
                                            onError?.invoke()
                                            continuation.resume(Unit)
                                            return@withLock
                                        }

                                        // KRİTİK: Yüklenen resource'un geçerli olduğundan emin ol
                                        // Bitmap set edilmeden önce zorunlu kontrol
                                        if (resource == null || isDrawableRecycled(resource)) {
                                            Timber.tag(TAG).w("⚠️ Yüklenen arkaplan null veya geçersiz (recycled), fallback kullanılıyor")
                                            isCancelled = false
                                            clearCache()
                                            onError?.invoke()
                                            continuation.resume(Unit)
                                            return@withLock
                                        }

                                        // Ekstra güvenlik: Bitmap içeriyorsa isRecycled kontrolü
                                        if (resource is BitmapDrawable) {
                                            val bitmap = resource.bitmap
                                            if (bitmap == null || bitmap.isRecycled) {
                                                Timber.tag(TAG).w("⚠️ Bitmap null veya recycled, cache'e kaydedilmiyor")
                                                isCancelled = false
                                                clearCache()
                                                onError?.invoke()
                                                continuation.resume(Unit)
                                                return@withLock
                                            }
                                        }

                                        // WeakReference ile kaydet - sadece geçerli drawable'lar kaydedilir
                                        setCachedDrawable(resource)
                                        Timber.tag(
                                            TAG,
                                        ).d("✅ Arkaplan görsel yüklendi (Glide): ${resource.intrinsicWidth}x${resource.intrinsicHeight}")
                                        onSuccess?.invoke(resource)
                                        continuation.resume(Unit)
                                    }
                                }
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {
                                // Görsel temizlendiğinde yapılacak işlemler
                            }

                            override fun onLoadFailed(errorDrawable: Drawable?) {
                                Timber.tag(TAG).e("❌ Glide arkaplan yükleme başarısız")
                                onError?.invoke()
                                continuation.resume(Unit)
                            }
                        },
                    )
            }
        } catch (e: OutOfMemoryError) {
            Timber.tag(TAG).e(e, "❌ Bellek hatası - Arkaplan yüklenemedi")
            // OutOfMemoryError'ı Crashlytics'e gönder - kritik hata
            try {
                CrashlyticsHelper.recordNonFatalException(
                    exception = e,
                    category = "memory",
                    priority = CrashlyticsHelper.Priority.CRITICAL,
                    tag = CrashlyticsHelper.Tags.MEMORY,
                    errorContext = "BackgroundManager.loadBackground - OutOfMemoryError",
                )
                CrashlyticsHelper.sendUnsentReports()
            } catch (ex: Exception) {
                Timber.e(ex, "Crashlytics'e OutOfMemoryError kaydedilemedi")
            }
            // Low memory durumunda cache'i temizle
            clearCache()
            onError?.invoke()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Arkaplan yükleme hatası: ${e.message}")
            // Exception'ı Crashlytics'e gönder
            try {
                CrashlyticsHelper.recordNonFatalException(
                    exception = e,
                    category = "background",
                    priority = CrashlyticsHelper.Priority.HIGH,
                    tag = CrashlyticsHelper.Tags.BACKGROUND,
                    errorContext = "BackgroundManager.loadBackground",
                )
                CrashlyticsHelper.sendUnsentReports()
            } catch (ex: Exception) {
                Timber.e(ex, "Crashlytics'e exception kaydedilemedi")
            }
            onError?.invoke()
        }
    }

    /**
     * Drawable içindeki bitmap'in recycle edilip edilmediğini kontrol eder.
     * BitmapDrawable, LayerDrawable ve diğer drawable türlerini destekler.
     *
     * @param drawable Kontrol edilecek Drawable
     * @return true ise bitmap recycle edilmiş veya geçersiz, false ise güvenli
     */
    private fun isDrawableRecycled(drawable: Drawable?): Boolean {
        if (drawable == null) return true

        return try {
            when (drawable) {
                is BitmapDrawable -> {
                    val bitmap = drawable.bitmap
                    bitmap == null || bitmap.isRecycled
                }
                is LayerDrawable -> {
                    // LayerDrawable içindeki tüm drawable'ları kontrol et
                    var recycled = false
                    for (i in 0 until drawable.numberOfLayers) {
                        val layerDrawable = drawable.getDrawable(i)
                        if (isDrawableRecycled(layerDrawable)) {
                            recycled = true
                            break
                        }
                    }
                    recycled
                }
                is ColorDrawable -> {
                    // ColorDrawable bitmap kullanmaz, her zaman güvenli
                    false
                }
                else -> {
                    // Diğer drawable türleri için güvenli varsayım
                    // Eğer drawable'ın kendisi geçersizse (intrinsicWidth/Height -1 dönerse) sorun olabilir
                    val width = drawable.intrinsicWidth
                    val height = drawable.intrinsicHeight
                    // -1 genellikle drawable'ın geçersiz olduğunu gösterir
                    width == -1 && height == -1
                }
            }
        } catch (e: Exception) {
            // Herhangi bir exception durumunda güvenli tarafta kal
            Timber.tag(TAG).w(e, "⚠️ Drawable kontrolü sırasında hata, güvenli tarafta kalınıyor")
            true
        }
    }

    /**
     * Cache'lenmiş arkaplanı döndürür.
     * Eğer yüklenmemişse veya bitmap recycle edilmişse null döner.
     * Bu metod, recycled bitmap'lerin kullanılmasını önler.
     * WeakReference kullanarak sistemin bellek yönetimine izin verir.
     */
    fun getCachedBackground(): Drawable? {
        // WeakReference'den al ve kontrol et
        val cached = getCachedDrawable()
        if (cached == null) {
            // WeakReference null döndüyse veya drawable silinmişse
            if (isLoaded) {
                Timber.tag(TAG).d("⚠️ Cache'lenmiş arkaplan WeakReference tarafından temizlenmiş (sistem bellek yönetimi)")
                isLoaded = false
            }
            return null
        }

        // Bitmap recycle kontrolü yap (ekstra güvenlik)
        if (isDrawableRecycled(cached)) {
            Timber.tag(TAG).w("⚠️ Cache'lenmiş arkaplan recycle edilmiş, cache temizleniyor")
            // Cache'i temizle - recycled bitmap'i kullanma
            clearCache()
            return null
        }

        return cached
    }

    /**
     * Cache'i temizler. Low memory durumlarında çağrılabilir.
     * Drawable callback'lerini de temizleyerek bellek sızıntısını önler.
     * Activity onStop olduğunda güvenli bir şekilde çağrılabilir.
     */
    fun clearCache() {
        // WeakReference'den drawable'ı al ve callback'lerini temizle
        val drawable = getCachedDrawable()
        drawable?.callback = null
        
        // WeakReference'i temizle
        cachedBackgroundRef = null
        isLoaded = false
        // Devam eden yükleme varsa iptal et
        isCancelled = true
        
        Timber.tag(TAG).d("🧹 Cache temizlendi")
    }

    /**
     * Cache'i yumuşak bir şekilde temizler (soft clear).
     * WeakReference'i null yapar ama isLoaded flag'ini korur.
     * Bu, disk cache'den hızlıca yeniden yüklenmesini sağlar.
     * Activity onStop olduğunda kullanılabilir.
     */
    fun softClearCache() {
        // Sadece WeakReference'i temizle, isLoaded flag'ini koru
        // Böylece disk cache'den hızlıca yeniden yüklenebilir
        cachedBackgroundRef = null
        Timber.tag(TAG).d("🧹 Cache yumuşak temizlendi (soft clear) - disk cache korunuyor")
    }

    /**
     * Ekran boyutunu piksel cinsinden döndürür.
     */
    private fun getScreenSize(context: Context): Pair<Int, Int> {
        val windowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return Pair(1280, 720) // Default fallback

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics?.bounds?.let { bounds ->
                return Pair(bounds.width(), bounds.height())
            }
        }

        // Fallback for older Android versions
        @Suppress("DEPRECATION")
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay?.getMetrics(displayMetrics)
        return Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    /**
     * Fallback arkaplan (renk) döndürür.
     * Görsel yüklenemediğinde kullanılır.
     * Büyük bitmap yükleme riskini tamamen ortadan kaldırmak için ColorDrawable kullanır.
     */
    fun getFallbackBackground(context: Context): Drawable? {
        return try {
            // Güvenli bir renk olan siyahı kullan. Bu, büyük bir bitmap yükleme riskini tamamen ortadan kaldırır.
            // ColorDrawable çok küçük bellek kullanır (birkaç byte) ve her zaman güvenlidir.
            ColorDrawable(Color.BLACK)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Fallback arkaplan rengi oluşturulamadı: ${e.message}")
            // Fallback arkaplan yükleme hatasını Crashlytics'e gönder
            try {
                CrashlyticsHelper.recordNonFatalException(
                    exception = e,
                    category = "background",
                    priority = CrashlyticsHelper.Priority.MEDIUM,
                    tag = CrashlyticsHelper.Tags.BACKGROUND,
                    errorContext = "BackgroundManager.getFallbackBackground",
                )
                CrashlyticsHelper.sendUnsentReports()
            } catch (ex: Exception) {
                Timber.e(ex, "Crashlytics'e fallback hatası kaydedilemedi")
            }
            null
        }
    }
}
