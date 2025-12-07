package com.pnr.tv.ui.browse

import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import coil.load
import coil.size.Scale
import com.pnr.tv.Constants
import com.pnr.tv.R
import com.pnr.tv.model.ContentItem

/**
 * İçerik kartlarının görünümünü tanımlayan Presenter sınıfı.
 * ContentItem interface'ini kullanarak tüm içerik tipleri (MovieEntity, SeriesEntity, LiveStreamEntity)
 * için ortak bir kart oluşturur.
 */
class CardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        // CustomImageCardView kullan - ImageCardView'ın internal layout sorununu çözmek için
        val cardView = CustomImageCardView(parent.context)

        // Ekran genişliğini al
        val screenWidth = parent.context.resources.displayMetrics.widthPixels
        // Her satırda 7 kart gösterilecek şekilde kart genişliğini hesapla
        // Ekran genişliğinin yaklaşık 1/7'si kadar (padding ve margin'ler için biraz küçük)
        val cardWidth = (screenWidth / Constants.CARD_WIDTH_DIVISOR).toInt()

        // Layout parametrelerini ayarla - genişlik dinamik olarak hesaplanmış
        val layoutParams =
            ViewGroup.LayoutParams(
                cardWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
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
