package com.pnr.tv.ui.player.adapter

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.R
import com.pnr.tv.ui.player.state.TrackInfo

class TrackSelectionAdapter(
    private var tracksList: List<TrackInfo>,
    private var selectedTrack: TrackInfo?,
    private val onTrackSelected: (TrackInfo) -> Unit,
    private val isAudioAdapter: Boolean = true, // Ses dilleri adapter'ı mı?
    private val audioRecyclerView: RecyclerView? = null, // Ses dilleri RecyclerView (alt yazılar için)
    private val subtitleRecyclerView: RecyclerView? = null, // Alt yazılar RecyclerView (ses dilleri için)
    private val saveButton: View? = null, // Kaydet butonu
) : RecyclerView.Adapter<TrackSelectionAdapter.TrackViewHolder>() {
    /**
     * Tracks listesini döndürür (focus yönetimi için erişim gerekli).
     */
    val tracks: List<TrackInfo>
        get() = tracksList
    
    fun updateTracks(
        newTracks: List<TrackInfo>,
        newSelected: TrackInfo?,
    ) {
        tracksList = newTracks
        selectedTrack = newSelected
        notifyDataSetChanged()
    }

    /**
     * Sadece seçili track'i günceller, tracks listesini korur.
     * UI'da kullanıcının tıkladığı öğenin "seçili" kalmasını sağlar.
     * ExoPlayer'dan gelen asıl güncelleme handleTracksChanged üzerinden akacaktır.
     * RecyclerView'ın tüm listeyi yenileyip odağı kaybetmemesi için sadece ilgili item'ları günceller.
     */
    fun updateSelectedTrack(newSelected: TrackInfo?) {
        val oldSelected = selectedTrack
        selectedTrack = newSelected
        
        // Sadece eski ve yeni seçili pozisyonları güncelle - tüm listeyi yenileme
        val oldPosition = if (oldSelected != null) {
            tracksList.indexOfFirst { it == oldSelected }
        } else {
            -1
        }
        
        val newPosition = if (newSelected != null) {
            tracksList.indexOfFirst { it == newSelected }
        } else {
            -1
        }
        
        // Sadece değişen item'ları güncelle
        if (oldPosition >= 0 && oldPosition < tracksList.size) {
            notifyItemChanged(oldPosition)
        }
        if (newPosition >= 0 && newPosition < tracksList.size && newPosition != oldPosition) {
            notifyItemChanged(newPosition)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): TrackViewHolder {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_track_selection, parent, false)
        return TrackViewHolder(view, isAudioAdapter, audioRecyclerView, subtitleRecyclerView, saveButton)
    }

    override fun onBindViewHolder(
        holder: TrackViewHolder,
        position: Int,
    ) {
        val track = tracksList[position]
        val isFirstItem = position == 0
        val isLastItem = position == tracksList.size - 1
        
        // Donanımsal Focus Kilidi - Android'e 'başka bir yer arama, aşağıda/yukarıda yine sensin' demek
        // Öğe render edilirken nextFocus ID'lerini çiviyle çakar gibi sabitle
        if (position == 0) {
            holder.itemView.nextFocusUpId = holder.itemView.id
        }
        if (position == tracksList.size - 1) {
            holder.itemView.nextFocusDownId = holder.itemView.id
        }
        
               holder.bind(track, track == selectedTrack, isFirstItem, isLastItem, position) {
                   val oldSelectedPosition = tracksList.indexOfFirst { it == selectedTrack }
                   selectedTrack = track

                   if (oldSelectedPosition >= 0 && oldSelectedPosition < tracksList.size) {
                notifyItemChanged(oldSelectedPosition)
            }
            notifyItemChanged(position)
            onTrackSelected(track)
        }
    }

    override fun getItemCount(): Int = tracksList.size

    class TrackViewHolder(
        itemView: View,
        private val isAudioAdapter: Boolean,
        private val audioRecyclerView: RecyclerView?,
        private val subtitleRecyclerView: RecyclerView?,
        private val saveButton: View?,
    ) : RecyclerView.ViewHolder(itemView) {
        private val radioButton: RadioButton = itemView.findViewById(R.id.radio_track)
        private val textView: TextView = itemView.findViewById(R.id.txt_track_name)

        fun bind(
            track: TrackInfo,
            isSelected: Boolean,
            isFirstItem: Boolean,
            isLastItem: Boolean,
            position: Int,
            onClick: () -> Unit,
        ) {
            textView.text = track.label ?: itemView.context.getString(R.string.unknown)
            radioButton.isChecked = isSelected

            // RecyclerView içindeki item'lar otomatik olarak birbirine bağlanır
            // Sadece RecyclerView'lar arası geçişleri ve kaydet butonuna geçişi ayarlıyoruz

            itemView.post {
                // RecyclerView içindeki item'lar için nextFocusUp/Down'ı ayarla
                val recyclerView = itemView.parent as? RecyclerView

                if (recyclerView != null) {
                    // İlk item'da yukarı basıldığında focus'un kendinde kalması için nextFocusUpId'yi kendine ayarla
                    if (isFirstItem) {
                        itemView.nextFocusUpId = itemView.id
                    } else {
                        // Bir önceki item
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
                    } else {
                        // En son item'da focus'un kendinde kalması için nextFocusDownId'yi kendine ayarla
                        itemView.nextFocusDownId = itemView.id
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
                       val currentFocused = itemView.isFocused
                       val currentPosition = position
                       onClick()
                       if (currentFocused) {
                           itemView.post {
                               val recyclerView = itemView.parent as? RecyclerView
                               recyclerView?.let { rv ->
                                   val viewHolder = rv.findViewHolderForAdapterPosition(currentPosition)
                                   viewHolder?.itemView?.requestFocus() ?: itemView.requestFocus()
                               } ?: itemView.requestFocus()
                           }
                       }
                   }

                   itemView.setOnKeyListener { view, keyCode, event ->
                       if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                           val wasFocused = view.isFocused
                           val currentPosition = position
                           onClick()
                           if (wasFocused) {
                               view.post {
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
                // Background zaten XML'de track_item_background olarak ayarlı
                // Focus durumu selector tarafından otomatik yönetiliyor

                if (hasFocus) {
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
                }
                // Focus durumu değiştiğinde background selector tarafından otomatik yönetiliyor
                // Ekstra kod gerekmiyor
            }
        }
    }
}
