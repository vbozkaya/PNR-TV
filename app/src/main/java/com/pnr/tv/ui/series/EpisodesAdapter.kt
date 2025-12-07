package com.pnr.tv.ui.series

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.R

class EpisodesAdapter(
    private val onEpisodeClick: (ParsedEpisode) -> Unit,
    private val onFocusUpToSeasons: () -> Unit,
) : ListAdapter<ParsedEpisode, EpisodesAdapter.EpisodeViewHolder>(EpisodeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_episode_chip, parent, false)
        return EpisodeViewHolder(view, onEpisodeClick, onFocusUpToSeasons)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class EpisodeViewHolder(
        itemView: View,
        private val onEpisodeClick: (ParsedEpisode) -> Unit,
        private val onFocusUpToSeasons: () -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val episodeTitle: TextView = itemView.findViewById(R.id.txt_episode_title)

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
        }

        fun bind(episode: ParsedEpisode) {
            // Standart metin göster (tüm kutucuklar aynı boyutta olsun)
            episodeTitle.text = "Bölüm ${episode.episodeNumber}"
            
            // İzlenme durumuna göre doğru çerçeveyi seç ve ata
            val borderDrawableRes = when (episode.watchStatus) {
                WatchStatus.NOT_WATCHED -> R.drawable.border_status_white
                WatchStatus.IN_PROGRESS -> R.drawable.border_status_red
                WatchStatus.FULLY_WATCHED -> R.drawable.border_status_green
            }
            (itemView as FrameLayout).foreground = ContextCompat.getDrawable(itemView.context, borderDrawableRes)

            itemView.setOnClickListener { onEpisodeClick(episode) }
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true
        }
    }

    private class EpisodeDiffCallback : DiffUtil.ItemCallback<ParsedEpisode>() {
        override fun areItemsTheSame(oldItem: ParsedEpisode, newItem: ParsedEpisode): Boolean {
            return oldItem.episodeId == newItem.episodeId
        }

        override fun areContentsTheSame(oldItem: ParsedEpisode, newItem: ParsedEpisode): Boolean {
            return oldItem == newItem
        }
    }
}
