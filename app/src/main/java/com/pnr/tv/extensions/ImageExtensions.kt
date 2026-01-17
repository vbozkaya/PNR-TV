package com.pnr.tv.extensions

import android.widget.ImageView
import coil.load
import coil.request.CachePolicy
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import com.pnr.tv.R
import com.pnr.tv.core.constants.UIConstants

/**
 * ImageView extension fonksiyonları.
 * Tutarlı image loading için merkezi helper metodlar.
 */

/**
 * İçerik kartları için image yükler.
 * Grid ve liste görünümlerinde kullanılır.
 *
 * @param imageUrl Yüklenecek görsel URL'i
 * @param cardWidth Kart genişliği (piksel)
 * @param cardHeight Kart yüksekliği (piksel)
 */
fun ImageView.loadContentImage(
    imageUrl: String?,
    cardWidth: Int,
    cardHeight: Int,
) {
    if (!imageUrl.isNullOrBlank()) {
        // Maksimum boyut limitleri - çok büyük bitmap'leri önlemek için
        // Android'in bitmap çizme limiti ~100MB, bu yüzden daha küçük boyutlar kullanıyoruz
        // 1280x720 RGB565 = ~1.8MB, güvenli bir limit (BackgroundManager ile tutarlı)
        val maxWidth = 1280
        val maxHeight = 720
        val finalWidth = cardWidth.coerceAtMost(maxWidth)
        val finalHeight = cardHeight.coerceAtMost(maxHeight)

        load(imageUrl) {
            placeholder(R.drawable.placeholder_image)
            error(R.drawable.placeholder_image)
            // TV performans optimizasyonu: Düşük işlemcili TV cihazlarında crossfade animasyonu
            // ana thread'i yorabilir. Anlık resim değişimi daha performanslıdır.
            // crossfade(ImageAnimations.CROSSFADE_DURATION_MS) // TV için kapatıldı
            scale(Scale.FILL)
            size(Size(finalWidth, finalHeight))
            // Precision ayarı - EXACT kullanarak belirtilen boyutlarda yükle
            // Bu, görselin daha büyük yüklenmesini önler ve bitmap çizme hatasını engeller
            precision(Precision.EXACT)
            allowHardware(true)
            allowRgb565(true)
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
            networkCachePolicy(CachePolicy.ENABLED)
        }
    } else {
        // URL yoksa placeholder göster - boyut limitleri ile
        val maxWidth = 1280
        val maxHeight = 720
        val finalWidth = cardWidth.coerceAtMost(maxWidth)
        val finalHeight = cardHeight.coerceAtMost(maxHeight)

        load(R.drawable.placeholder_image) {
            scale(Scale.FIT)
            size(Size(finalWidth, finalHeight))
            precision(Precision.EXACT)
            allowHardware(true)
            allowRgb565(true)
        }
    }
}

/**
 * Poster görselleri için image yükler.
 * Detail sayfalarında kullanılır (MovieDetail, SeriesDetail).
 * ImageView'ın gerçek boyutunu kullanır veya maksimum limitleri uygular.
 *
 * @param imageUrl Yüklenecek görsel URL'i
 * @param maxWidth Maksimum genişlik (piksel, varsayılan: 1280)
 * @param maxHeight Maksimum yükseklik (piksel, varsayılan: 720)
 */
fun ImageView.loadPosterImage(
    imageUrl: String?,
    maxWidth: Int = 1280,
    maxHeight: Int = 720,
) {
    // Maksimum boyut limitleri - çok büyük bitmap'leri önlemek için
    // Android'in bitmap çizme limiti ~100MB, bu yüzden daha küçük boyutlar kullanıyoruz
    // 1280x720 RGB565 = ~1.8MB, güvenli bir limit (BackgroundManager ile tutarlı)
    // Her zaman maksimum limitleri uygula - ImageView'ın gerçek boyutunu kullanmaya çalışma
    // çünkü bu güvenilir değil ve büyük ekranlarda sorun çıkarabilir
    val absoluteMaxWidth = 1280
    val absoluteMaxHeight = 720

    // Parametrelerden gelen değerler ve mutlak maksimum limitler arasından en küçüğünü kullan
    val finalWidth = minOf(maxWidth, absoluteMaxWidth)
    val finalHeight = minOf(maxHeight, absoluteMaxHeight)

    if (!imageUrl.isNullOrBlank()) {
        load(imageUrl) {
            placeholder(R.drawable.live)
            error(R.drawable.live)
            // TV performans optimizasyonu: Düşük işlemcili TV cihazlarında crossfade animasyonu
            // ana thread'i yorabilir. Anlık resim değişimi daha performanslıdır.
            // crossfade(ImageAnimations.CROSSFADE_DURATION_MS) // TV için kapatıldı
            scale(Scale.FILL)
            // Bitmap boyutunu sınırla - çok büyük görselleri önlemek için
            size(Size(finalWidth, finalHeight))
            // Precision ayarı - EXACT kullanarak belirtilen boyutlarda yükle
            // Bu, görselin daha büyük yüklenmesini önler
            precision(Precision.EXACT)
            // Hardware bitmap'leri aktif et - performans ve bellek kullanımı için
            // allowHardware(true) ile bitmap GPU'da saklanır, daha az RAM kullanır
            allowHardware(true)
            // RGB565 formatı - daha az bellek kullanımı (16-bit vs 32-bit)
            allowRgb565(true)
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
            networkCachePolicy(CachePolicy.ENABLED)
        }
    } else {
        // URL yoksa placeholder göster - boyut limitleri ile
        load(R.drawable.live) {
            scale(Scale.FILL)
            size(Size(finalWidth, finalHeight))
            precision(Precision.EXACT)
            allowHardware(true)
            allowRgb565(true)
        }
    }
}

/**
 * Kart görselleri için image yükler.
 * CardPresenter'da kullanılır (Leanback library).
 *
 * @param imageUrl Yüklenecek görsel URL'i
 * @param cardWidth Kart genişliği (piksel)
 * @param cardHeight Kart yüksekliği (piksel)
 */
fun ImageView.loadCardImage(
    imageUrl: String?,
    cardWidth: Int,
    cardHeight: Int,
) {
    if (!imageUrl.isNullOrBlank()) {
        // Maksimum boyut limitleri - çok büyük bitmap'leri önlemek için
        // Android'in bitmap çizme limiti ~100MB, bu yüzden daha küçük boyutlar kullanıyoruz
        // 1280x720 RGB565 = ~1.8MB, güvenli bir limit (BackgroundManager ile tutarlı)
        val maxWidth = 1280
        val maxHeight = 720
        val finalWidth = cardWidth.coerceAtMost(maxWidth)
        val finalHeight = cardHeight.coerceAtMost(maxHeight)

        load(imageUrl) {
            placeholder(R.drawable.placeholder_image)
            error(R.drawable.placeholder_image)
            // TV performans optimizasyonu: Düşük işlemcili TV cihazlarında crossfade animasyonu
            // ana thread'i yorabilir. Anlık resim değişimi daha performanslıdır.
            // crossfade(ImageAnimations.CROSSFADE_DURATION_MS) // TV için kapatıldı
            scale(Scale.FILL)
            size(Size(finalWidth, finalHeight))
            // Precision ayarı - EXACT kullanarak belirtilen boyutlarda yükle
            // Bu, görselin daha büyük yüklenmesini önler ve bitmap çizme hatasını engeller
            precision(Precision.EXACT)
            allowHardware(true)
            allowRgb565(true)
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
            networkCachePolicy(CachePolicy.ENABLED)
        }
    } else {
        // URL yoksa placeholder göster - boyut limitleri ile
        val maxWidth = 1280
        val maxHeight = 720
        val finalWidth = cardWidth.coerceAtMost(maxWidth)
        val finalHeight = cardHeight.coerceAtMost(maxHeight)

        load(R.drawable.placeholder_image) {
            scale(Scale.FIT)
            size(Size(finalWidth, finalHeight))
            precision(Precision.EXACT)
            allowHardware(true)
            allowRgb565(true)
        }
    }
}

/**
 * Ekran genişliğine göre kart boyutlarını hesaplar.
 * Maksimum boyut limitleri ile çok büyük bitmap'leri önler.
 *
 * @return Pair<Int, Int> - (genişlik, yükseklik) piksel cinsinden
 */
fun ImageView.calculateCardSize(): Pair<Int, Int> {
    val screenWidth = context.resources.displayMetrics.widthPixels
    val calculatedWidth = (screenWidth / UIConstants.CARD_WIDTH_DIVISOR).toInt()
    val calculatedHeight = (calculatedWidth * 9.0 / 16.0).toInt()

    // Maksimum boyut limitleri - çok büyük ekranlarda veya hesaplanan boyutlar
    // çok büyük olduğunda bitmap çizme hatasını önlemek için
    // 1280x720 RGB565 = ~1.8MB, Android'in ~100MB limit'inden çok daha güvenli (BackgroundManager ile tutarlı)
    val maxWidth = 1280
    val maxHeight = 720

    val cardWidth = calculatedWidth.coerceAtMost(maxWidth)
    val cardHeight = calculatedHeight.coerceAtMost(maxHeight)

    return Pair(cardWidth, cardHeight)
}
