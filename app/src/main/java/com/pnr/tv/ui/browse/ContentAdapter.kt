package com.pnr.tv.ui.browse

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import coil.size.Scale
import coil.size.Size
import com.pnr.tv.R
import com.pnr.tv.model.ContentItem

/**
 * Generic content adapter that works with ContentItem interface.
 * Replaces MoviesAdapter, SeriesAdapter, and ChannelsAdapter.
 *
 * Supports:
 * - Remote control / DPAD navigation
 * - Click events
 * - Long press events
 * - Left/right directional navigation (category list ←→ content grid)
 * - OK button long press (3 seconds) for favorites (optional)
 */
class ContentAdapter(
    private val onContentClick: (ContentItem) -> Unit,
    private val onContentLongPress: (ContentItem) -> Unit,
    private val gridColumnCount: Int,
    private val onOkButtonLongPress: ((ContentItem) -> Unit)? = null, // Optional: for 3-second OK button press
) : ListAdapter<ContentItem, ContentAdapter.ViewHolder>(ContentDiff) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_channel, parent, false)
        return ViewHolder(view, onContentClick, onContentLongPress, onOkButtonLongPress, this, gridColumnCount)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position), position, gridColumnCount)
    }

    class ViewHolder(
        itemView: View,
        private val onContentClick: (ContentItem) -> Unit,
        private val onContentLongPress: (ContentItem) -> Unit,
        private val onOkButtonLongPress: ((ContentItem) -> Unit)?,
        private val adapter: ContentAdapter,
        private val gridColumnCount: Int,
    ) : RecyclerView.ViewHolder(itemView) {
        private val contentImage: ImageView = itemView.findViewById(R.id.channel_image)
        private val contentName: TextView = itemView.findViewById(R.id.channel_name)
        private val imageContainer: ViewGroup = itemView.findViewById(R.id.image_container)
        private val ratingBadge: TextView = itemView.findViewById(R.id.rating_badge)

        // OK tuşu için 3 saniyelik basılı tutma desteği
        private var okButtonPressHandler: Handler? = null
        private var okButtonPressRunnable: Runnable? = null
        private var isOkButtonPressed = false
        private var isOkButtonLongPressTriggered = false // 3 saniye geçti mi?
        private val OK_BUTTON_LONG_PRESS_DURATION = 3000L // 3 saniye

        init {
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true
        }

        /**
         * Binds the content item to the view holder.
         */
        fun bind(
            item: ContentItem,
            position: Int,
            gridColumnCount: Int,
        ) {
            contentName.text = item.title

            // Rating badge gösterimi - sadece film ve diziler için
            val rating =
                when (item) {
                    is com.pnr.tv.db.entity.MovieEntity -> item.rating
                    is com.pnr.tv.db.entity.SeriesEntity -> item.rating
                    else -> null
                }

            if (rating != null) {
                ratingBadge.text = String.format("%.1f", rating)
                ratingBadge.visibility = View.VISIBLE
            } else {
                ratingBadge.visibility = View.GONE
            }

            // Set image scale type to fitXY (fill the card completely) for normal images
            // Placeholder durumunda FIT_CENTER kullanılacak (orantıyı korumak için)
            contentImage.scaleType = ImageView.ScaleType.FIT_XY

            // Show white border container and constrain ImageView to container
            imageContainer.visibility = View.VISIBLE
            val layoutParams = contentImage.layoutParams as? ConstraintLayout.LayoutParams
            // Border kalınlığı kadar padding ekle (2dp) - ImageView border'ın içinde kalmalı
            val borderWidthPx = (2f * itemView.context.resources.displayMetrics.density).toInt()
            layoutParams?.let {
                it.startToStart = R.id.image_container
                it.endToEnd = R.id.image_container
                it.topToTop = R.id.image_container
                it.bottomToBottom = R.id.image_container
                it.dimensionRatio = null // Will be taken from container
                // Border kalınlığı kadar margin ekle - ImageView border'ın içinde kalmalı
                it.setMargins(borderWidthPx, borderWidthPx, borderWidthPx, borderWidthPx)
                contentImage.layoutParams = it
            }

            // Add corner radius to ImageView (to stay inside the border)
            // Border container'ın corner radius'u 7dp, border kalınlığı 2dp
            // ImageView'ın corner radius'u border container'ın corner radius'undan border kalınlığı kadar küçük olmalı
            val borderCornerRadius = 7f // poster_border.xml'deki corner radius
            val borderWidth = 2f // poster_border.xml'deki stroke width
            val imageViewCornerRadius = (borderCornerRadius - borderWidth) * itemView.context.resources.displayMetrics.density
            contentImage.clipToOutline = true
            contentImage.viewTreeObserver.addOnGlobalLayoutListener(
                object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        contentImage.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        contentImage.outlineProvider =
                            object : android.view.ViewOutlineProvider() {
                                override fun getOutline(
                                    view: android.view.View,
                                    outline: android.graphics.Outline,
                                ) {
                                    outline.setRoundRect(0, 0, view.width, view.height, imageViewCornerRadius)
                                }
                            }
                    }
                },
            )

            // Load image with Coil
            val imageUrl = item.imageUrl
            if (!imageUrl.isNullOrBlank()) {
                // Dinamik boyut hesapla - CardPresenter ile aynı optimizasyon
                val screenWidth = itemView.context.resources.displayMetrics.widthPixels
                val cardWidth = (screenWidth / com.pnr.tv.UIConstants.CARD_WIDTH_DIVISOR).toInt()
                val cardHeight = (cardWidth * 9.0 / 16.0).toInt()

                contentImage.load(imageUrl) {
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
                // Use placeholder if no image URL - üst kısma hizalamak için scaleType değiştir
                contentImage.scaleType = ImageView.ScaleType.FIT_START
                // Görseli üst kısma yaklaştırmak için ImageView'ın gravity'sini ayarla
                // FIT_START sol üst köşeye hizalar, bu yüzden görseli üst kısma yaklaştırır
                contentImage.load(R.drawable.placeholder_image) {
                    scale(Scale.FIT) // Orantıyı koru
                }
                // Görseli üst kısma yaklaştırmak için ImageView'ın padding'ini ayarla
                val topPadding = (8f * itemView.context.resources.displayMetrics.density).toInt()
                val bottomPadding = (60f * itemView.context.resources.displayMetrics.density).toInt()
                contentImage.setPadding(0, topPadding, 0, bottomPadding)
            }

            // Set up item-specific click listeners
            itemView.setOnClickListener {
                // Eğer OK tuşu long press tetiklendiyse, normal click'i engelle
                if (!isOkButtonLongPressTriggered) {
                    onContentClick(item)
                }
                // Flag'i sıfırla
                isOkButtonLongPressTriggered = false
            }

            itemView.setOnLongClickListener {
                onContentLongPress(item)
                true
            }

            // OK tuşu için 3 saniyelik basılı tutma desteği (sadece callback sağlanmışsa)
            if (onOkButtonLongPress != null) {
                setupOkButtonLongPress(item)
            } else {
                // Callback yoksa, mevcut handler'ı temizle
                clearOkButtonHandler()
            }
        }

        /**
         * OK tuşuna 3 saniye basılı tutulduğunda favori işlemi yapar.
         */
        private fun setupOkButtonLongPress(item: ContentItem) {
            // Önceki handler'ı temizle
            clearOkButtonHandler()

            itemView.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                    when (event.action) {
                        KeyEvent.ACTION_DOWN -> {
                            // OK tuşu basıldı
                            isOkButtonPressed = true
                            isOkButtonLongPressTriggered = false
                            okButtonPressHandler = Handler(Looper.getMainLooper())
                            okButtonPressRunnable =
                                Runnable {
                                    // 3 saniye geçti, long press callback'ini çağır
                                    if (isOkButtonPressed) {
                                        isOkButtonLongPressTriggered = true
                                        onOkButtonLongPress?.invoke(item)
                                        isOkButtonPressed = false
                                    }
                                }
                            // 3 saniye sonra çalışacak runnable'ı planla
                            okButtonPressHandler?.postDelayed(okButtonPressRunnable!!, OK_BUTTON_LONG_PRESS_DURATION)
                            // Olayı tüketme, normal click davranışı da çalışsın
                            false
                        }
                        KeyEvent.ACTION_UP -> {
                            // OK tuşu bırakıldı
                            isOkButtonPressed = false
                            clearOkButtonHandler()
                            // Eğer 3 saniye geçmediyse, normal click davranışı çalışsın
                            // (setOnClickListener zaten bunu handle ediyor)
                            // Eğer 3 saniye geçtiyse, isOkButtonLongPressTriggered flag'i zaten true olacak
                            // ve click listener içinde normal click engellenecek
                            false
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
        }

        /**
         * OK tuşu handler'ını temizler.
         */
        private fun clearOkButtonHandler() {
            okButtonPressRunnable?.let { runnable ->
                okButtonPressHandler?.removeCallbacks(runnable)
            }
            okButtonPressHandler = null
            okButtonPressRunnable = null
            isOkButtonPressed = false
            // isOkButtonLongPressTriggered flag'ini sıfırlama, click listener içinde sıfırlanacak
        }
    }
}
