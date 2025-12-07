package com.pnr.tv.ui.browse

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
import coil.size.Scale
import com.pnr.tv.Constants
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
 */
class ContentAdapter(
    private val onContentClick: (ContentItem) -> Unit,
    private val onContentLongPress: (ContentItem) -> Unit,
    private val onFocusLeftFromGrid: () -> Unit,
    private val onNavigateUpFromTopRow: () -> Unit,
    private val gridColumnCount: Int,
) : ListAdapter<ContentItem, ContentAdapter.ViewHolder>(ContentDiff) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_channel, parent, false)
        return ViewHolder(view, onContentClick, onContentLongPress, onFocusLeftFromGrid, onNavigateUpFromTopRow, this)
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
        private val onFocusLeftFromGrid: () -> Unit,
        private val onNavigateUpFromTopRow: () -> Unit,
        private val adapter: ContentAdapter,
    ) : RecyclerView.ViewHolder(itemView) {
        private val contentImage: ImageView = itemView.findViewById(R.id.channel_image)
        private val contentName: TextView = itemView.findViewById(R.id.channel_name)
        private val imageContainer: ViewGroup = itemView.findViewById(R.id.image_container)
        private val ratingBadge: TextView = itemView.findViewById(R.id.rating_badge)

        init {
            setupKeyListener()
            setupClickListeners()
        }

        /**
         * Sets up key event listener for DPAD navigation.
         * Focus'un grid dışına çıkmasını engeller (sadece sol yön tuşu ve back tuşu hariç).
         */
        private fun setupKeyListener() {
            itemView.setOnKeyListener { _, keyCode, event ->
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        // Sol yön tuşu - kategori listesine gitmek için izin ver
                        val currentPosition = bindingAdapterPosition
                        if (currentPosition == RecyclerView.NO_POSITION) {
                            return@setOnKeyListener false
                        }

                        if (currentPosition % Constants.GRID_COLUMN_COUNT == 0) {
                            // En sol sütundaysak, kategori listesine git
                            if (event.action == KeyEvent.ACTION_DOWN) {
                                onFocusLeftFromGrid()
                            }
                            // Key'i consume et
                            return@setOnKeyListener true
                        }
                        false
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Aşağı yön tuşu - son satırdaysak engelle
                        val itemCount = adapter.itemCount
                        val currentPosition = bindingAdapterPosition

                        if (currentPosition == RecyclerView.NO_POSITION) {
                            return@setOnKeyListener false
                        }

                        val lastRowStart =
                            if (itemCount <= Constants.GRID_COLUMN_COUNT) {
                                0
                            } else {
                                itemCount - Constants.GRID_COLUMN_COUNT
                            }
                        if (currentPosition >= lastRowStart && currentPosition < itemCount) {
                            // Son satırdaysak, key'i consume et - focus'un grid dışına çıkmasını engelle
                            return@setOnKeyListener true
                        }
                        false
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        // Yukarı yön tuşu - ilk satırdaysak engelle
                        val currentPosition = bindingAdapterPosition
                        if (currentPosition == RecyclerView.NO_POSITION) {
                            return@setOnKeyListener false
                        }

                        if (currentPosition < Constants.GRID_COLUMN_COUNT) {
                            // İlk satırdaysak, key'i consume et
                            return@setOnKeyListener true
                        }
                        false
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        // Sağ yön tuşu - en sağ sütundaysak engelle
                        val currentPosition = bindingAdapterPosition
                        if (currentPosition == RecyclerView.NO_POSITION) {
                            return@setOnKeyListener false
                        }

                        if (currentPosition % Constants.GRID_COLUMN_COUNT == Constants.GRID_COLUMN_COUNT - 1) {
                            // En sağ sütundaysak, key'i consume et
                            return@setOnKeyListener true
                        }
                        false
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        // Back tuşu - izin ver
                        false
                    }
                    else -> {
                        // Diğer tuşlar için default davranış
                        false
                    }
                }
            }
        }

        /**
         * Sets up click and long press listeners.
         */
        private fun setupClickListeners() {
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
            // Yukarı yön tuşu için özel listener - ilk satırdaysa navbar'a git
            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    // Eğer bu kart ilk satırdaysa (pozisyonu sütun sayısından küçükse)
                    if (position < gridColumnCount) {
                        // Fragment'a haber ver.
                        onNavigateUpFromTopRow()
                        return@setOnKeyListener true // Olayı tüket, sistemin karışmasını engelle.
                    }
                }

                // Diğer yönler için mevcut mantığı koru
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        val currentPosition = bindingAdapterPosition
                        if (currentPosition == RecyclerView.NO_POSITION) {
                            return@setOnKeyListener false
                        }

                        if (currentPosition % Constants.GRID_COLUMN_COUNT == 0) {
                            if (event.action == KeyEvent.ACTION_DOWN) {
                                onFocusLeftFromGrid()
                            }
                            return@setOnKeyListener true
                        }
                        false
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val itemCount = adapter.itemCount
                        val currentPosition = bindingAdapterPosition

                        if (currentPosition == RecyclerView.NO_POSITION) {
                            return@setOnKeyListener false
                        }

                        val lastRowStart =
                            if (itemCount <= Constants.GRID_COLUMN_COUNT) {
                                0
                            } else {
                                itemCount - Constants.GRID_COLUMN_COUNT
                            }
                        if (currentPosition >= lastRowStart && currentPosition < itemCount) {
                            return@setOnKeyListener true
                        }
                        false
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val currentPosition = bindingAdapterPosition
                        if (currentPosition == RecyclerView.NO_POSITION) {
                            return@setOnKeyListener false
                        }

                        if ((currentPosition + 1) % Constants.GRID_COLUMN_COUNT == 0) {
                            return@setOnKeyListener true
                        }
                        false
                    }
                    else -> false
                }
            }

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
                contentImage.load(imageUrl) {
                    placeholder(R.drawable.placeholder_image)
                    error(R.drawable.placeholder_image)
                    crossfade(true)
                    scale(Scale.FILL)
                }
            } else {
                // Use placeholder if no image URL - orantıyı korumak için scaleType değiştir
                contentImage.scaleType = ImageView.ScaleType.FIT_CENTER
                contentImage.load(R.drawable.placeholder_image) {
                    scale(Scale.FIT) // Orantıyı koru
                }
            }

            // Set up item-specific click listeners
            itemView.setOnClickListener {
                onContentClick(item)
            }

            itemView.setOnLongClickListener {
                onContentLongPress(item)
                true
            }
        }
    }
}
