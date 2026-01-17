package com.pnr.tv.util.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.ImageView
import coil.load
import coil.request.CachePolicy
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import timber.log.Timber

/**
 * Güvenli görsel yükleme utility class'ı.
 * Büyük bitmap'lerin bellek taşmasına neden olmasını önler.
 * BitmapFactory.Options ile inSampleSize kullanarak görselleri optimize eder.
 *
 * Özellikler:
 * - Ekran boyutuna göre otomatik ölçeklendirme
 * - Maksimum boyut limitleri ile güvenli yükleme
 * - Coil ile optimize edilmiş yükleme
 * - Bellek dostu bitmap formatı (RGB565)
 */
object SafeImageLoader {
    private const val TAG = "SafeImageLoader"

    // Maksimum boyut limitleri - Canvas çizim hatasını önlemek için
    // Android'in bitmap çizme limiti ~100MB, pratik limitler daha düşük olmalı
    private const val MAX_BITMAP_WIDTH = 1920 // 1080p genişlik
    private const val MAX_BITMAP_HEIGHT = 1080 // 1080p yükseklik

    // Minimum boyut limitleri - çok küçük bitmap'lerden kaçınmak için
    private const val MIN_BITMAP_WIDTH = 480
    private const val MIN_BITMAP_HEIGHT = 320

    /**
     * ImageView'a drawable resource'u güvenli bir şekilde yükler.
     * Coil kullanarak ekran boyutuna göre optimize eder.
     *
     * @param imageView Görselin yükleneceği ImageView
     * @param drawableRes Drawable resource ID
     * @param maxWidth Maksimum genişlik (piksel), null ise ekran boyutuna göre hesaplanır
     * @param maxHeight Maksimum yükseklik (piksel), null ise ekran boyutuna göre hesaplanır
     */
    fun loadDrawableSafely(
        imageView: ImageView,
        drawableRes: Int,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
    ) {
        val context = imageView.context

        // Ekran boyutunu al
        val screenSize = getScreenSize(context)

        // Maksimum boyutları hesapla
        val calculatedMaxWidth = maxWidth ?: screenSize.first.coerceIn(MIN_BITMAP_WIDTH, MAX_BITMAP_WIDTH)
        val calculatedMaxHeight = maxHeight ?: screenSize.second.coerceIn(MIN_BITMAP_HEIGHT, MAX_BITMAP_HEIGHT)

        // Final boyutlar - ImageView boyutunu da dikkate al
        val imageViewWidth = if (imageView.width > 0) imageView.width else calculatedMaxWidth
        val imageViewHeight = if (imageView.height > 0) imageView.height else calculatedMaxHeight

        val finalWidth = minOf(imageViewWidth, calculatedMaxWidth).coerceAtLeast(MIN_BITMAP_WIDTH)
        val finalHeight = minOf(imageViewHeight, calculatedMaxHeight).coerceAtLeast(MIN_BITMAP_HEIGHT)

        Timber.tag(TAG).d(
            "🖼️ Güvenli görsel yükleme: drawable=$drawableRes, " +
                "finalSize=${finalWidth}x$finalHeight, screenSize=${screenSize.first}x${screenSize.second}",
        )

        // Coil ile optimize edilmiş yükleme
        imageView.load(drawableRes) {
            scale(Scale.FILL)
            size(Size(finalWidth, finalHeight))
            precision(Precision.INEXACT) // INEXACT daha az bellek kullanır
            allowHardware(true) // Donanım hızlandırmayı etkinleştir
            allowRgb565(true) // RGB565 formatı - daha az bellek kullanımı
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
        }
    }

    /**
     * Drawable resource'u Bitmap olarak güvenli bir şekilde yükler.
     * BitmapFactory.Options ile inSampleSize kullanarak optimize eder.
     *
     * @param context Context
     * @param drawableRes Drawable resource ID
     * @param reqWidth İstenen genişlik (piksel)
     * @param reqHeight İstenen yükseklik (piksel)
     * @return Optimize edilmiş Bitmap veya null (hata durumunda)
     */
    fun loadBitmapSafely(
        context: Context,
        drawableRes: Int,
        reqWidth: Int = MAX_BITMAP_WIDTH,
        reqHeight: Int = MAX_BITMAP_HEIGHT,
    ): Bitmap? {
        return try {
            // Önce sadece boyutları oku
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

            val inputStream = context.resources.openRawResource(drawableRes)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // inSampleSize hesapla
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // Optimize edilmiş seçeneklerle decode et
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565 // Bellek kullanımı azalt

            val inputStream2 = context.resources.openRawResource(drawableRes)
            val bitmap = BitmapFactory.decodeStream(inputStream2, null, options)
            inputStream2.close()

            if (bitmap != null) {
                Timber.tag(TAG).d(
                    "✅ Bitmap optimize edildi: ${bitmap.width}x${bitmap.height}, " +
                        "inSampleSize=${options.inSampleSize}, Format=RGB_565",
                )
            } else {
                Timber.tag(TAG).w("⚠️ Bitmap null döndü: drawable=$drawableRes")
            }

            bitmap
        } catch (e: OutOfMemoryError) {
            Timber.tag(TAG).e(e, "❌ Bellek hatası - Bitmap yüklenemedi: drawable=$drawableRes")
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Bitmap yükleme hatası: drawable=$drawableRes, error=${e.message}")
            null
        }
    }

    /**
     * Bitmap'in ne kadar küçültüleceğini hesaplar (inSampleSize).
     * Bu, bellek taşmalarını önlemek için kritik bir fonksiyondur.
     *
     * @param options BitmapFactory.Options (inJustDecodeBounds = true ile çağrılmalı)
     * @param reqWidth İstenen genişlik (piksel)
     * @param reqHeight İstenen yükseklik (piksel)
     * @return inSampleSize değeri (1, 2, 4, 8, 16, ... şeklinde 2'nin kuvvetleri)
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1

        // Eğer görsel istenen boyuttan büyükse, küçültme gerekir
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Görsel boyutunun yarısı hala istenen boyuttan büyükse,
            // inSampleSize'ı artırarak devam et
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        Timber.tag(TAG).d(
            "📏 inSampleSize hesaplandı: $inSampleSize " +
                "(Orijinal: ${width}x$height, İstenen: ${reqWidth}x$reqHeight)",
        )
        return inSampleSize
    }

    /**
     * Ekran boyutunu piksel cinsinden döndürür.
     */
    private fun getScreenSize(context: Context): Pair<Int, Int> {
        val windowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return Pair(1920, 1080) // Default fallback (1080p)

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
     * Bitmap'in bellekte kapladığı alanı tahmin eder (byte cinsinden).
     * Debug ve logging için kullanılabilir.
     */
    fun estimateBitmapSize(bitmap: Bitmap): Long {
        return bitmap.byteCount.toLong()
    }
}
