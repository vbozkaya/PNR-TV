package com.pnr.tv.ui.viewers

import android.content.Context
import android.view.LayoutInflater
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
        }
    }
}
