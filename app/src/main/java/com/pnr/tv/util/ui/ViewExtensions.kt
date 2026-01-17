package com.pnr.tv.util.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * View extension fonksiyonları.
 * Arka plan yükleme gibi ortak işlemler için kullanılır.
 */

/**
 * View'a arka planı güvenli bir şekilde set eder.
 * Bitmap recycle kontrolü yaparak "Canvas: trying to use a recycled bitmap" hatasını önler.
 *
 * @param drawable Set edilecek Drawable
 */
fun View.setBackgroundSafely(drawable: Drawable?) {
    if (drawable == null) {
        this.background = null
        return
    }

    // Bitmap recycle kontrolü yap
    if (isDrawableRecycled(drawable)) {
        Timber.tag("BACKGROUND").w("⚠️ Recycled bitmap tespit edildi, fallback kullanılıyor")
        // Recycled bitmap'i set etme, fallback kullan
        val context = this.context
        val fallback = BackgroundManager.getFallbackBackground(context)
        this.background = fallback
        return
    }

    // Güvenli, set et
    this.background = drawable
}

/**
 * Drawable içindeki bitmap'in recycle edilip edilmediğini kontrol eder.
 * View'a set etmeden önce mutlaka kontrol edilmelidir.
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
        Timber.tag("BACKGROUND").w(e, "⚠️ Drawable kontrolü sırasında hata, güvenli tarafta kalınıyor")
        true
    }
}

/**
 * View'a arka plan görselini BackgroundManager kullanarak yükler.
 * Cache'lenmiş görseli önce kontrol eder, yoksa yükler.
 * Bitmap recycle kontrolü yaparak güvenli bir şekilde arka planı set eder.
 *
 * @param context Context (Activity veya Application)
 */
suspend fun View.loadBackgroundFromManager(context: Context) {
    withContext(Dispatchers.Main) {
        // Önce cache'den kontrol et (hızlı)
        // getCachedBackground zaten recycle kontrolü yapıyor
        val cached = BackgroundManager.getCachedBackground()
        if (cached != null) {
            // Güvenli set et
            this@loadBackgroundFromManager.setBackgroundSafely(cached)
            Timber.tag("BACKGROUND").d("✅ Arkaplan uygulandı (cache'den)")
            return@withContext
        }

        // Cache'de yoksa veya geçersizse yükle
        BackgroundManager.loadBackground(
            context = context,
            imageLoader = null, // Artık kullanılmıyor, Glide kullanılıyor
            onSuccess = { drawable ->
                // Güvenli set et - setBackgroundSafely zaten recycle kontrolü yapıyor
                this@loadBackgroundFromManager.setBackgroundSafely(drawable)
                Timber.tag(
                    "BACKGROUND",
                ).d("✅ Arkaplan uygulandı - View background: ${this@loadBackgroundFromManager.background?.javaClass?.simpleName}")
            },
            onError = {
                Timber.tag("BACKGROUND").w("⚠️ onError callback çağrıldı, fallback deneniyor...")
                // Hata durumunda fallback kullan (theme'de zaten tanımlı)
                val fallback = BackgroundManager.getFallbackBackground(context)
                this@loadBackgroundFromManager.setBackgroundSafely(fallback)
            },
        )
    }
}
