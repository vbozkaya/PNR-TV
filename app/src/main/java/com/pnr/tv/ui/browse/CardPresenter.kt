package com.pnr.tv.ui.browse

import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import coil.load
import coil.request.CachePolicy
import coil.size.Scale
import coil.size.Size
import com.pnr.tv.Constants
import com.pnr.tv.R
import com.pnr.tv.model.ContentItem
import kotlin.math.roundToInt

/**
 * İçerik kartlarının görünümünü tanımlayan Presenter sınıfı.
 * ContentItem interface'ini kullanarak tüm içerik tipleri (MovieEntity, SeriesEntity, LiveStreamEntity)
 * için ortak bir kart oluşturur.
 */
class CardPresenter : Presenter() {
    private var cardWidth: Int = 0
    private var cardHeight: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        // CustomImageCardView kullan - ImageCardView'ın internal layout sorununu çözmek için
        val cardView = CustomImageCardView(parent.context)

        // Kart boyutunu sadece bir kez hesapla
        if (cardWidth == 0) {
            val screenWidth = parent.context.resources.displayMetrics.widthPixels
            cardWidth = (screenWidth / Constants.CARD_WIDTH_DIVISOR).toInt()
            // 16:9 en-boy oranı varsayımıyla yüksekliği hesapla
            cardHeight = (cardWidth * 9.0 / 16.0).roundToInt()
        }

        // Layout parametrelerini ayarla - genişlik dinamik olarak hesaplanmış
        val layoutParams = ViewGroup.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        cardView.layoutParams = layoutParams

        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        item: Any?,
    ) {
        val cardView = viewHolder.view as CustomImageCardView

        // Item'ı ContentItem interface'ine dönüştür
        val contentItem = item as? ContentItem

        if (contentItem == null) {
            // Item null veya ContentItem'a dönüştürülemiyorsa placeholder göster
            cardView.titleText = ""
            cardView.contentText = ""
            cardView.mainImageView?.load(R.drawable.placeholder_image) {
                scale(Scale.FIT)
            }
            return
        }

        // Başlığı ayarla
        cardView.titleText = contentItem.title
        cardView.contentText = ""

        // Resim yükleme mantığı
        val imageUrl = contentItem.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            // URL varsa, Coil ile yükle
            cardView.mainImageView?.load(imageUrl) {
                placeholder(R.drawable.placeholder_image)
                error(R.drawable.placeholder_image)
                crossfade(true)
                scale(Scale.FILL)
                // Dinamik ve verimli boyut kullan - kart boyutuna göre optimize edilmiş
                size(Size(cardWidth, cardHeight))
                // Donanım hızlandırmayı etkinleştir - GPU belleği kullanımı için kritik
                allowHardware(true)
                // RGB565 formatını kullan - daha az bellek kullanır
                allowRgb565(true)
                // Cache policy optimizasyonları - performans için
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                networkCachePolicy(CachePolicy.ENABLED)
            }
        } else {
            // URL yoksa, placeholder göster (orantıyı koru)
            cardView.mainImageView?.load(R.drawable.placeholder_image) {
                scale(Scale.FIT)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as CustomImageCardView
        cardView.mainImage = null
    }
}
