package com.pnr.tv.ui.player

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.R

class TrackSelectionAdapter(
    private var tracks: List<TrackInfo>,
    private var selectedTrack: TrackInfo?,
    private val onTrackSelected: (TrackInfo) -> Unit,
    private val isAudioAdapter: Boolean = true, // Ses dilleri adapter'ı mı?
    private val audioRecyclerView: RecyclerView? = null, // Ses dilleri RecyclerView (alt yazılar için)
    private val subtitleRecyclerView: RecyclerView? = null, // Alt yazılar RecyclerView (ses dilleri için)
    private val saveButton: View? = null // Kaydet butonu
) : RecyclerView.Adapter<TrackSelectionAdapter.TrackViewHolder>() {

    fun updateTracks(newTracks: List<TrackInfo>, newSelected: TrackInfo?) {
        tracks = newTracks
        selectedTrack = newSelected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track_selection, parent, false)
        return TrackViewHolder(view, isAudioAdapter, audioRecyclerView, subtitleRecyclerView, saveButton)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = tracks[position]
        val isFirstItem = position == 0
        val isLastItem = position == tracks.size - 1
        holder.bind(track, track == selectedTrack, isFirstItem, isLastItem, position) {
            // Önceki seçili item'ın pozisyonunu bul
            val oldSelectedPosition = tracks.indexOfFirst { it == selectedTrack }
            
            // Yeni seçimi yap
            selectedTrack = track
            
            // Sadece değişen item'ları güncelle (focus kaybını önlemek için)
            if (oldSelectedPosition >= 0 && oldSelectedPosition < tracks.size) {
                notifyItemChanged(oldSelectedPosition) // Eski seçili item'ı güncelle
            }
            notifyItemChanged(position) // Yeni seçili item'ı güncelle
            
            onTrackSelected(track)
        }
    }

    override fun getItemCount(): Int = tracks.size

    class TrackViewHolder(
        itemView: View,
        private val isAudioAdapter: Boolean,
        private val audioRecyclerView: RecyclerView?,
        private val subtitleRecyclerView: RecyclerView?,
        private val saveButton: View?
    ) : RecyclerView.ViewHolder(itemView) {
        private val radioButton: RadioButton = itemView.findViewById(R.id.radio_track)
        private val textView: TextView = itemView.findViewById(R.id.txt_track_name)

        fun bind(track: TrackInfo, isSelected: Boolean, isFirstItem: Boolean, isLastItem: Boolean, position: Int, onClick: () -> Unit) {
            textView.text = track.label ?: itemView.context.getString(R.string.unknown)
            radioButton.isChecked = isSelected
            
            // RecyclerView içindeki item'lar otomatik olarak birbirine bağlanır
            // Sadece RecyclerView'lar arası geçişleri ve kaydet butonuna geçişi ayarlıyoruz
            
            itemView.post {
                // RecyclerView içindeki item'lar için nextFocusUp/Down'ı ayarla
                val recyclerView = itemView.parent as? RecyclerView
                
                if (recyclerView != null) {
                    // Bir önceki item
                    if (!isFirstItem) {
                        val prevViewHolder = recyclerView.findViewHolderForAdapterPosition(position - 1)
                        prevViewHolder?.itemView?.let { prevItem ->
                            itemView.nextFocusUpId = prevItem.id
                        }
                    }
                    
                    // Bir sonraki item
                    if (!isLastItem) {
                        val nextViewHolder = recyclerView.findViewHolderForAdapterPosition(position + 1)
                        nextViewHolder?.itemView?.let { nextItem ->
                            itemView.nextFocusDownId = nextItem.id
                        }
                    }
                    
                    // RecyclerView'lar arası geçişler (sadece alt yazılar için)
                    if (!isAudioAdapter) {
                        // Alt yazılar - son item'dan kaydet butonuna
                        if (isLastItem && saveButton != null) {
                            itemView.nextFocusDownId = saveButton.id
                        }
                    }
                }
            }
            
            itemView.setOnClickListener {
                // Focus'u koru - seçim yapıldığında focus bu item'da kalsın
                val currentFocused = itemView.isFocused
                val currentPosition = position
                onClick()
                // Focus'u aynı item'da tut - notifyItemChanged sonrası
                if (currentFocused) {
                    itemView.post {
                        // RecyclerView'dan aynı pozisyondaki item'ı bul ve focus ver
                        val recyclerView = itemView.parent as? RecyclerView
                        recyclerView?.let { rv ->
                            val viewHolder = rv.findViewHolderForAdapterPosition(currentPosition)
                            viewHolder?.itemView?.requestFocus() ?: itemView.requestFocus()
                        } ?: itemView.requestFocus()
                    }
                }
            }
            
            // OK tuşu ile seçim
            itemView.setOnKeyListener { view, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                    val wasFocused = view.isFocused
                    val currentPosition = position
                    onClick()
                    // Focus'u koru - notifyItemChanged sonrası
                    if (wasFocused) {
                        view.post {
                            // RecyclerView'dan aynı pozisyondaki item'ı bul ve focus ver
                            val recyclerView = view.parent as? RecyclerView
                            recyclerView?.let { rv ->
                                val viewHolder = rv.findViewHolderForAdapterPosition(currentPosition)
                                viewHolder?.itemView?.requestFocus() ?: view.requestFocus()
                            } ?: view.requestFocus()
                        }
                    }
                    true
                } else {
                    false
                }
            }
            
            itemView.setOnFocusChangeListener { focusedView, hasFocus ->
                if (hasFocus) {
                    itemView.background = itemView.context.getDrawable(R.drawable.button_focus_background)
                    
                    // Focus alındığında item'ı MUTLAKA görünür alana getir - direkt scroll, animasyon YOK
                    val recyclerView = focusedView.parent as? RecyclerView
                    if (recyclerView != null) {
                        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                        if (layoutManager != null) {
                            val focusedPosition = recyclerView.getChildAdapterPosition(focusedView)
                            if (focusedPosition != RecyclerView.NO_POSITION) {
                                // Direkt scroll - animasyon yok, her zaman scroll yap
                                recyclerView.post {
                                    // Item'ın tam görünürlüğünü kontrol et
                                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                                    
                                    // Item görünür değilse veya sınırlardaysa, direkt scroll yap
                                    var needsScroll = false
                                    if (focusedPosition < firstVisible || focusedPosition > lastVisible) {
                                        needsScroll = true
                                    } else {
                                        // Item görünür ama tam görünmüyor olabilir - kontrol et
                                        val viewHolder = recyclerView.findViewHolderForAdapterPosition(focusedPosition)
                                        viewHolder?.itemView?.let { view ->
                                            val top = view.top
                                            val bottom = view.bottom
                                            val recyclerTop = recyclerView.paddingTop
                                            val recyclerBottom = recyclerView.height - recyclerView.paddingBottom
                                            
                                            // Item'ın üstü veya altı taşıyorsa scroll yap
                                            if (top < recyclerTop || bottom > recyclerBottom) {
                                                needsScroll = true
                                            }
                                        }
                                    }
                                    
                                    // Her zaman scroll yap - item'ı görünür alana getir
                                    if (needsScroll || focusedPosition == firstVisible || focusedPosition == lastVisible) {
                                        // Item'ı üstte konumlandır (padding ile biraz boşluk bırak)
                                        layoutManager.scrollToPositionWithOffset(focusedPosition, recyclerView.paddingTop + 20)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            }
        }
    }
}
