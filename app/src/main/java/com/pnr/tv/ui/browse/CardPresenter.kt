package com.pnr.tv.ui.browse

import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import coil.load
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import com.pnr.tv.R
import com.pnr.tv.core.constants.UIConstants
import com.pnr.tv.extensions.loadCardImage
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
            val calculatedWidth = (screenWidth / UIConstants.CARD_WIDTH_DIVISOR).toInt()
            // 16:9 en-boy oranı varsayımıyla yüksekliği hesapla
            val calculatedHeight = (calculatedWidth * 9.0 / 16.0).roundToInt()

            // Maksimum boyut limitleri - çok büyük ekranlarda bitmap çizme hatasını önlemek için
            // 1280x720 RGB565 = ~1.8MB, güvenli bir limit
            val maxWidth = 1280
            val maxHeight = 720

            cardWidth = calculatedWidth.coerceAtMost(maxWidth)
            cardHeight = calculatedHeight.coerceAtMost(maxHeight)
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
            // Placeholder için de boyut limitleri ve precision ekle
            val maxWidth = 1280
            val maxHeight = 720
            val finalWidth = cardWidth.coerceAtMost(maxWidth)
            val finalHeight = cardHeight.coerceAtMost(maxHeight)

            cardView.mainImageView?.load(R.drawable.placeholder_image) {
                scale(Scale.FIT)
                size(Size(finalWidth, finalHeight))
                precision(Precision.EXACT)
                allowHardware(true)
                allowRgb565(true)
            }
            return
        }

        // Başlığı ayarla
        cardView.titleText = contentItem.title
        cardView.contentText = ""

        // Resim yükleme mantığı - helper extension kullan
        cardView.mainImageView?.loadCardImage(
            imageUrl = contentItem.imageUrl,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
        )
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as CustomImageCardView
        cardView.mainImage = null
    }
}
