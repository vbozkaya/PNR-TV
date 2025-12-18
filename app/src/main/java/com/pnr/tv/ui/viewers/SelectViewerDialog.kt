package com.pnr.tv.ui.viewers

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import com.pnr.tv.R
import com.pnr.tv.databinding.DialogSelectViewerBinding
import com.pnr.tv.db.entity.ViewerEntity
import com.pnr.tv.util.BackgroundManager
import kotlinx.coroutines.launch

class SelectViewerDialog(
    private val context: Context,
    private val viewers: List<ViewerEntity>,
    private val onViewerSelected: (ViewerEntity) -> Unit,
    private val lifecycleScope: LifecycleCoroutineScope? = null, // Optional: for background loading
) {
    fun show() {
        val binding = DialogSelectViewerBinding.inflate(LayoutInflater.from(context))
        var dialog: AlertDialog? = null
        var selectedViewer: ViewerEntity? = null

        lateinit var adapter: SelectViewerAdapter
        adapter =
            SelectViewerAdapter(viewers, null) { viewer ->
                selectedViewer = viewer
                adapter.updateSelectedViewer(viewer)
            }

        binding.recyclerViewers.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        binding.recyclerViewers.adapter = adapter

        // Arka plan görselini yükle
        loadDialogBackground(binding.dialogBackgroundImage)

        dialog =
            AlertDialog.Builder(context)
                .setTitle(R.string.dialog_select_viewer_title)
                .setView(binding.root)
                .setNegativeButton(R.string.dialog_cancel) { _, _ ->
                    dialog?.dismiss()
                }
                .setPositiveButton(R.string.dialog_ok) { _, _ ->
                    selectedViewer?.let { viewer ->
                        onViewerSelected(viewer)
                    }
                    dialog?.dismiss()
                }
                .create()

        val window = dialog.window
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        // Arka plan şeffaf olmasın, çerçeve gösterilsin
        window?.setBackgroundDrawableResource(R.drawable.navbar_background)

        dialog.show()

        // Dialog açıldığında RecyclerView'ın ilk öğesine focus ver
        binding.recyclerViewers.post {
            if (adapter.itemCount > 0) {
                val firstItem = binding.recyclerViewers.findViewHolderForAdapterPosition(0)
                firstItem?.itemView?.requestFocus()
            }
        }
    }

    /**
     * Dialog arka plan görselini yükler (ana sayfada kullanılan görsel).
     */
    private fun loadDialogBackground(imageView: android.widget.ImageView) {
        val scope = lifecycleScope ?: return // Eğer lifecycle scope yoksa, görsel yüklenmez

        scope.launch {
            // Önce cache'den kontrol et
            val cached = BackgroundManager.getCachedBackground()
            if (cached != null) {
                imageView.setImageDrawable(cached)
                return@launch
            }

            // Cache'de yoksa yükle
            BackgroundManager.loadBackground(
                context = context,
                imageLoader = context.imageLoader,
                onSuccess = { drawable ->
                    imageView.setImageDrawable(drawable)
                },
                onError = {
                    // Hata durumunda fallback kullan
                    val fallback = BackgroundManager.getFallbackBackground(context)
                    if (fallback != null) {
                        imageView.setImageDrawable(fallback)
                    }
                },
            )
        }
    }
}

class SelectViewerAdapter(
    private val viewers: List<ViewerEntity>,
    private var selectedViewer: ViewerEntity?,
    private val onViewerClick: (ViewerEntity) -> Unit,
) : RecyclerView.Adapter<SelectViewerAdapter.ViewHolder>() {
    fun updateSelectedViewer(viewer: ViewerEntity?) {
        val oldPosition = selectedViewer?.let { viewers.indexOf(it) }?.takeIf { it >= 0 }
        selectedViewer = viewer
        val newPosition = viewer?.let { viewers.indexOf(it) }?.takeIf { it >= 0 }

        oldPosition?.let { notifyItemChanged(it) }
        newPosition?.let { notifyItemChanged(it) }
    }

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
        val viewer = viewers[position]
        val isSelected = selectedViewer?.id == viewer.id
        holder.bind(viewer, isSelected)
    }

    override fun getItemCount(): Int = viewers.size

    class ViewHolder(
        itemView: android.view.View,
        private val onViewerClick: (ViewerEntity) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: android.widget.TextView = itemView.findViewById(R.id.tv_viewer_name)

        fun bind(
            viewer: ViewerEntity,
            isSelected: Boolean,
        ) {
            nameTextView.text = viewer.name
            // Seçili izleyiciyi görsel olarak işaretle
            if (isSelected) {
                itemView.alpha = 1.0f
                ContextCompat.getDrawable(itemView.context, R.drawable.navyfocus_selector)?.let {
                    itemView.background = it
                }
            } else {
                itemView.alpha = 0.7f
                ContextCompat.getDrawable(itemView.context, R.drawable.second_focus_selector)?.let {
                    itemView.background = it
                }
            }
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
