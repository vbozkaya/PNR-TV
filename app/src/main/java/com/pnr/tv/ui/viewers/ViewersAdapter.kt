package com.pnr.tv.ui.viewers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.R
import com.pnr.tv.db.entity.ViewerEntity

class ViewersAdapter(
    private val onDeleteClick: (ViewerEntity) -> Unit,
) : ListAdapter<ViewerEntity, ViewersAdapter.ViewHolder>(DiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viewer, parent, false)
        return ViewHolder(view, onDeleteClick)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val onDeleteClick: (ViewerEntity) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.tv_viewer_name)
        val deleteButton: Button = itemView.findViewById(R.id.btn_delete)

        fun bind(viewer: ViewerEntity) {
            nameTextView.text = viewer.name
            deleteButton.visibility = if (viewer.isDeletable) View.VISIBLE else View.GONE
            deleteButton.setOnClickListener {
                onDeleteClick(viewer)
            }
            
            // Focus scroll: Sil butonu focus alındığında item'ı görünür alana getir
            deleteButton.setOnFocusChangeListener { focusedButton, hasFocus ->
                if (hasFocus) {
                    val recyclerView = (focusedButton.parent as? View)?.parent as? RecyclerView
                    if (recyclerView != null) {
                        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                        if (layoutManager != null) {
                            val adapterPosition = adapterPosition
                            if (adapterPosition != RecyclerView.NO_POSITION) {
                                recyclerView.post {
                                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                                    
                                    var needsScroll = false
                                    if (adapterPosition < firstVisible || adapterPosition > lastVisible) {
                                        needsScroll = true
                                    } else {
                                        val viewHolder = recyclerView.findViewHolderForAdapterPosition(adapterPosition)
                                        viewHolder?.itemView?.let { view ->
                                            val top = view.top
                                            val bottom = view.bottom
                                            val recyclerTop = recyclerView.paddingTop
                                            val recyclerBottom = recyclerView.height - recyclerView.paddingBottom
                                            
                                            if (top < recyclerTop || bottom > recyclerBottom) {
                                                needsScroll = true
                                            }
                                        }
                                    }
                                    
                                    if (needsScroll || adapterPosition == firstVisible || adapterPosition == lastVisible) {
                                        layoutManager.scrollToPositionWithOffset(adapterPosition, recyclerView.paddingTop + 20)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ViewerEntity>() {
        override fun areItemsTheSame(
            oldItem: ViewerEntity,
            newItem: ViewerEntity,
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: ViewerEntity,
            newItem: ViewerEntity,
        ): Boolean {
            return oldItem == newItem
        }
    }
}



