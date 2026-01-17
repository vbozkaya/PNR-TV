package com.pnr.tv.ui.series

import android.graphics.Outline
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import com.google.android.flexbox.FlexboxLayoutManager
import com.pnr.tv.R

class EpisodesAdapter(
    private val onEpisodeClick: (ParsedEpisode) -> Unit,
    private val onFocusUpToSeasons: () -> Unit,
) : ListAdapter<ParsedEpisode, EpisodesAdapter.EpisodeViewHolder>(EpisodeDiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_episode_chip, parent, false)
        return EpisodeViewHolder(view, onEpisodeClick, onFocusUpToSeasons)
    }

    override fun onBindViewHolder(
        holder: EpisodeViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    class EpisodeViewHolder(
        itemView: View,
        private val onEpisodeClick: (ParsedEpisode) -> Unit,
        private val onFocusUpToSeasons: () -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val episodeTitle: TextView = itemView.findViewById(R.id.txt_episode_title)
        private val episodeBackground: ImageView = itemView.findViewById(R.id.img_episode_background)

        init {
            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (bindingAdapterPosition == 0) {
                        onFocusUpToSeasons()
                        return@setOnKeyListener true
                    }
                }
                false
            }

            // ImageView'a corner radius ekle (CardView'un corner radius'u ile eşleştir: 8dp)
            val cornerRadius = 8f * itemView.context.resources.displayMetrics.density
            episodeBackground.outlineProvider =
                object : ViewOutlineProvider() {
                    override fun getOutline(
                        view: View,
                        outline: Outline,
                    ) {
                        outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                    }
                }
            episodeBackground.clipToOutline = true
        }

        fun bind(episode: ParsedEpisode) {
            // Standart metin göster (tüm kutucuklar aynı boyutta olsun)
            episodeTitle.text = itemView.context.getString(R.string.episode_format, episode.episodeNumber)

            // Bölüm arka plan görselini Coil ile yükle - optimize edilmiş boyut limitleri ile
            // Layout'ta 160dp x 56dp olarak tanımlı, ImageView'ın gerçek boyutunu kullan
            episodeBackground.post {
                // ImageView'ın gerçek boyutunu al (layout pass'ten sonra)
                val imageWidth =
                    if (episodeBackground.width > 0) {
                        episodeBackground.width
                    } else {
                        // Eğer henüz ölçülmemişse, dp'den px'e çevir (160dp x 56dp)
                        (160f * itemView.context.resources.displayMetrics.density).toInt()
                    }
                val imageHeight =
                    if (episodeBackground.height > 0) {
                        episodeBackground.height
                    } else {
                        (56f * itemView.context.resources.displayMetrics.density).toInt()
                    }

                // Optimize edilmiş maksimum boyut limitleri - ImageView boyutunun 2 katı (güvenlik payı)
                // Çok küçük bitmap'lerden kaçınmak için minimum değerler
                val minWidth = 320
                val minHeight = 112
                // Maksimum değerler - ImageView'ın gerçek boyutuna göre optimize edilmiş
                val maxWidth = (imageWidth * 2).coerceAtMost(640) // En fazla 640px (4x güvenlik payı)
                val maxHeight = (imageHeight * 2).coerceAtMost(224) // En fazla 224px (4x güvenlik payı)

                val finalWidth = imageWidth.coerceIn(minWidth, maxWidth)
                val finalHeight = imageHeight.coerceIn(minHeight, maxHeight)

                // Statik drawable'ı Coil ile yükle - bellek dostu optimizasyonlar ile
                episodeBackground.load(R.drawable.episode_background) {
                    scale(Scale.FILL)
                    size(Size(finalWidth, finalHeight))
                    precision(Precision.INEXACT) // INEXACT daha az bellek kullanır, görsel kalitesi yeterli
                    allowHardware(true)
                    allowRgb565(true)
                    memoryCachePolicy(CachePolicy.ENABLED) // Memory cache'i aktif et
                    diskCachePolicy(CachePolicy.ENABLED) // Disk cache'i aktif et
                }
            }

            // İzlenme durumuna göre doğru çerçeveyi seç ve ata
            val borderDrawableRes =
                when (episode.watchStatus) {
                    WatchStatus.NOT_WATCHED -> R.drawable.border_status_white
                    WatchStatus.IN_PROGRESS -> R.drawable.border_status_red
                    WatchStatus.FULLY_WATCHED -> R.drawable.border_status_green
                }
            (itemView as FrameLayout).foreground = ContextCompat.getDrawable(itemView.context, borderDrawableRes)

            itemView.setOnClickListener { onEpisodeClick(episode) }
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true

            // Focus scroll: Focus alındığında item'ı görünür alana getir
            itemView.setOnFocusChangeListener { focusedView, hasFocus ->
                if (hasFocus) {
                    val recyclerView = focusedView.parent as? RecyclerView
                    if (recyclerView != null) {
                        val layoutManager = recyclerView.layoutManager
                        if (layoutManager is FlexboxLayoutManager) {
                            val focusedPosition = recyclerView.getChildAdapterPosition(focusedView)
                            if (focusedPosition != RecyclerView.NO_POSITION) {
                                recyclerView.post {
                                    // FlexboxLayoutManager için scroll yap
                                    recyclerView.smoothScrollToPosition(focusedPosition)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private class EpisodeDiffCallback : DiffUtil.ItemCallback<ParsedEpisode>() {
        override fun areItemsTheSame(
            oldItem: ParsedEpisode,
            newItem: ParsedEpisode,
        ): Boolean {
            return oldItem.episodeId == newItem.episodeId
        }

        override fun areContentsTheSame(
            oldItem: ParsedEpisode,
            newItem: ParsedEpisode,
        ): Boolean {
            return oldItem == newItem
        }
    }
}
