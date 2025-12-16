package com.pnr.tv.ui.viewers

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.R
import com.pnr.tv.databinding.DialogSelectViewerBinding
import com.pnr.tv.db.entity.ViewerEntity

class SelectViewerDialog(
    private val context: Context,
    private val viewers: List<ViewerEntity>,
    private val onViewerSelected: (ViewerEntity) -> Unit,
) {
    fun show() {
        val binding = DialogSelectViewerBinding.inflate(LayoutInflater.from(context))
        var dialog: AlertDialog? = null

        val adapter =
            SelectViewerAdapter(viewers) { viewer ->
                onViewerSelected(viewer)
                dialog?.dismiss()
            }

        binding.recyclerViewers.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        binding.recyclerViewers.adapter = adapter

        dialog =
            AlertDialog.Builder(context)
                .setTitle(R.string.dialog_select_viewer_title)
                .setView(binding.root)
                .setNegativeButton(R.string.dialog_no, null)
                .create()

        val window = dialog.window
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.show()

        // Dialog açıldığında RecyclerView'ın ilk öğesine focus ver
        binding.recyclerViewers.post {
            if (adapter.itemCount > 0) {
                val firstItem = binding.recyclerViewers.findViewHolderForAdapterPosition(0)
                firstItem?.itemView?.requestFocus()
            }
        }
    }
}

class SelectViewerAdapter(
    private val viewers: List<ViewerEntity>,
    private val onViewerClick: (ViewerEntity) -> Unit,
) : RecyclerView.Adapter<SelectViewerAdapter.ViewHolder>() {
    override fun onCreateViewHolder(
        parent: android.view.ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viewer_select, parent, false)
        return ViewHolder(view, onViewerClick)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(viewers[position])
    }

    override fun getItemCount(): Int = viewers.size

    class ViewHolder(
        itemView: android.view.View,
        private val onViewerClick: (ViewerEntity) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: android.widget.TextView = itemView.findViewById(R.id.tv_viewer_name)

        fun bind(viewer: ViewerEntity) {
            nameTextView.text = viewer.name
            itemView.setOnClickListener {
                onViewerClick(viewer)
            }

            // Focus scroll: Focus alındığında item'ı görünür alana getir
            itemView.setOnFocusChangeListener { focusedView, hasFocus ->
                if (hasFocus) {
                    val recyclerView = focusedView.parent as? RecyclerView
                    if (recyclerView != null) {
                        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                        if (layoutManager != null) {
                            val focusedPosition = recyclerView.getChildAdapterPosition(focusedView)
                            if (focusedPosition != RecyclerView.NO_POSITION) {
                                recyclerView.post {
                                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                                    val lastVisible = layoutManager.findLastVisibleItemPosition()

                                    var needsScroll = false
                                    if (focusedPosition < firstVisible || focusedPosition > lastVisible) {
                                        needsScroll = true
                                    } else {
                                        val viewHolder = recyclerView.findViewHolderForAdapterPosition(focusedPosition)
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

                                    if (needsScroll || focusedPosition == firstVisible || focusedPosition == lastVisible) {
                                        layoutManager.scrollToPositionWithOffset(focusedPosition, recyclerView.paddingTop + 20)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
