package com.pnr.tv.ui.browse

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.BaseCardView
import com.pnr.tv.R

/**
 * ImageCardView yerine kullanılan custom card view.
 * ImageCardView'ın internal layout sorununu çözmek için oluşturuldu.
 */
class CustomImageCardView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : BaseCardView(context, attrs, defStyleAttr) {
        private val imageView: ImageView
        private val titleTextView: TextView
        private val contentTextView: TextView

        init {
            isFocusable = true
            isFocusableInTouchMode = true

            // Layout'u inflate et - attachToRoot = true ile ekle
            val inflater = LayoutInflater.from(context)
            inflater.inflate(R.layout.custom_image_card_view, this, true)

            // Child view'ları bul
            imageView = findViewById(R.id.card_image)
            titleTextView = findViewById(R.id.card_title)
            contentTextView = findViewById(R.id.card_content)

            // Card view ayarları
            cardType = BaseCardView.CARD_TYPE_INFO_UNDER
            setInfoVisibility(BaseCardView.CARD_REGION_VISIBLE_ALWAYS)
        }

        var titleText: CharSequence?
            get() = titleTextView.text
            set(value) {
                titleTextView.text = value
            }

        var contentText: CharSequence?
            get() = contentTextView.text
            set(value) {
                contentTextView.text = value
            }

        var mainImageView: ImageView?
            get() = imageView
            set(_) {
                // Read-only
            }

        var mainImage: android.graphics.drawable.Drawable?
            get() = imageView.drawable
            set(value) {
                imageView.setImageDrawable(value)
            }
    }
