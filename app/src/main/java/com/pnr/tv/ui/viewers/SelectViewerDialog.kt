package com.pnr.tv.ui.viewers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.R
import com.pnr.tv.databinding.DialogSelectViewerBinding
import com.pnr.tv.db.entity.ViewerEntity
import timber.log.Timber

class SelectViewerDialog(
    private val context: Context,
    private val viewers: List<ViewerEntity>,
    private val onViewerSelected: (ViewerEntity) -> Unit,
) {
    fun show() {
        val binding = DialogSelectViewerBinding.inflate(LayoutInflater.from(context))
        var dialog: AlertDialog? = null
        var selectedViewer: ViewerEntity? = null

        lateinit var adapter: SelectViewerAdapter
        Timber.tag("SelectViewerDialog").w("📋 SelectViewerDialog.show() - Adapter oluşturuluyor, viewers count=${viewers.size}")
        adapter =
            SelectViewerAdapter(viewers, null, binding.recyclerViewers) { viewer ->
                Timber.tag("SelectViewerDialog").w("👆 onViewerClick çağrıldı - viewer=${viewer.name}")
                selectedViewer = viewer
                Timber.tag("SelectViewerDialog").w("🔄 adapter.updateSelectedViewer çağrılıyor")
                adapter.updateSelectedViewer(viewer)
                Timber.tag("SelectViewerDialog").w("✅ adapter.updateSelectedViewer tamamlandı")
            }

        binding.recyclerViewers.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        binding.recyclerViewers.adapter = adapter

        dialog =
            AlertDialog.Builder(context)
                .setView(binding.root)
                .create()

        val window = dialog.window
        // Dialog sabit genişlikte olduğu için window layout'u wrap_content yapıyoruz
        window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        // Arka plan şeffaf yapılıyor (CardView kendi arka planını kullanacak)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Buton click listener'ları
        binding.btnDialogCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnDialogOk.setOnClickListener {
            selectedViewer?.let { viewer ->
                onViewerSelected(viewer)
            }
            dialog.dismiss()
        }

        // Buton focus listener'ları (TV remote için)
        binding.btnDialogCancel.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.btnDialogCancel.alpha = 1.0f
            } else {
                binding.btnDialogCancel.alpha = 0.7f
            }
        }

        binding.btnDialogOk.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.btnDialogOk.alpha = 1.0f
            } else {
                binding.btnDialogOk.alpha = 0.7f
            }
        }

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
    var selectedViewer: ViewerEntity?,
    private val recyclerView: RecyclerView?,
    private val onViewerClick: (ViewerEntity) -> Unit,
) : RecyclerView.Adapter<SelectViewerAdapter.ViewHolder>() {
    fun updateSelectedViewer(viewer: ViewerEntity?) {
        Timber.tag("SelectViewerAdapter").w("🚀 updateSelectedViewer METODU ÇAĞRILDI - viewer=${viewer?.name}")

        val oldPosition = selectedViewer?.let { viewers.indexOf(it) }?.takeIf { it >= 0 }
        selectedViewer = viewer
        val newPosition = viewer?.let { viewers.indexOf(it) }?.takeIf { it >= 0 }

        Timber.tag("SelectViewerAdapter").w("🔄 updateSelectedViewer: oldPosition=$oldPosition, newPosition=$newPosition")

        // RecyclerView layout/scroll işlemi sırasında notifyItemChanged çağrılmasını önlemek için
        // Her zaman post ile erteliyoruz - bu en güvenli yöntem
        recyclerView?.post {
            safeNotifyItemChanged(oldPosition, newPosition)
        } ?: run {
            // RecyclerView referansı yoksa Handler kullan (fallback)
            Timber.tag("SelectViewerAdapter").w("⚠️ RecyclerView referansı yok, Handler kullanılıyor")
            Handler(Looper.getMainLooper()).post {
                safeNotifyItemChanged(oldPosition, newPosition)
            }
        }
    }

    /**
     * Güvenli notifyItemChanged çağrısı - hata yakalama ile
     * Her zaman post içinden çağrılmalı
     */
    private fun safeNotifyItemChanged(
        oldPosition: Int?,
        newPosition: Int?,
    ) {
        // RecyclerView durumunu kontrol et
        val isLayoutRequested = recyclerView?.isLayoutRequested ?: false
        val isComputingLayout = recyclerView?.isComputingLayout ?: false
        val isLayoutSuppressed = recyclerView?.isLayoutSuppressed ?: false

        Timber.tag(
            "SelectViewerAdapter",
        ).w(
            "🔔 safeNotifyItemChanged: oldPosition=$oldPosition, newPosition=$newPosition, isLayoutRequested=$isLayoutRequested, isComputingLayout=$isComputingLayout",
        )

        // RecyclerView meşgulse, biraz bekle ve tekrar dene
        if (isLayoutRequested || isComputingLayout || isLayoutSuppressed) {
            Timber.tag("SelectViewerAdapter").w("⏳ RecyclerView meşgul, 100ms sonra tekrar deneniyor")
            recyclerView?.postDelayed({
                safeNotifyItemChanged(oldPosition, newPosition)
            }, 100)
            return
        }

        // RecyclerView hazırsa notifyItemChanged çağır
        try {
            Timber.tag("SelectViewerAdapter").w("✅ RecyclerView hazır, notifyItemChanged çağrılıyor")
            oldPosition?.let {
                notifyItemChanged(it)
                Timber.tag("SelectViewerAdapter").w("✅ notifyItemChanged($it) başarılı")
            }
            newPosition?.let {
                notifyItemChanged(it)
                Timber.tag("SelectViewerAdapter").w("✅ notifyItemChanged($it) başarılı")
            }
        } catch (e: IllegalStateException) {
            Timber.tag("SelectViewerAdapter").e(e, "❌ notifyItemChanged hatası: ${e.message}, 100ms sonra tekrar deneniyor")
            // Hata olursa, biraz bekle ve tekrar dene
            recyclerView?.postDelayed({
                try {
                    oldPosition?.let { notifyItemChanged(it) }
                    newPosition?.let { notifyItemChanged(it) }
                    Timber.tag("SelectViewerAdapter").w("✅ Delayed notifyItemChanged başarılı")
                } catch (e2: IllegalStateException) {
                    Timber.tag(
                        "SelectViewerAdapter",
                    ).e(e2, "❌ Delayed notifyItemChanged de başarısız: ${e2.message}, 200ms sonra son deneme")
                    recyclerView?.postDelayed({
                        try {
                            oldPosition?.let { notifyItemChanged(it) }
                            newPosition?.let { notifyItemChanged(it) }
                            Timber.tag("SelectViewerAdapter").w("✅ 3. deneme başarılı")
                        } catch (e3: IllegalStateException) {
                            Timber.tag("SelectViewerAdapter").e(e3, "❌ 3 deneme sonrası başarısız: ${e3.message}")
                        }
                    }, 200)
                }
            }, 100)
        } catch (e: Exception) {
            Timber.tag("SelectViewerAdapter").e(e, "❌ Beklenmeyen hata: ${e.message}")
        }
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
        private val selectedIndicator: android.widget.TextView? = itemView.findViewById(R.id.iv_selected_indicator)

        fun bind(
            viewer: ViewerEntity,
            isSelected: Boolean,
        ) {
            nameTextView.text = viewer.name

            // Seçili izleyiciyi görsel olarak işaretle
            if (isSelected) {
                itemView.alpha = 1.0f
                selectedIndicator?.visibility = android.view.View.VISIBLE
                ContextCompat.getDrawable(itemView.context, R.drawable.navyfocus_selector)?.let {
                    itemView.background = it
                }
            } else {
                itemView.alpha = 0.7f
                selectedIndicator?.visibility = android.view.View.GONE
                ContextCompat.getDrawable(itemView.context, R.drawable.second_focus_selector)?.let {
                    itemView.background = it
                }
            }

            itemView.setOnClickListener {
                Timber.tag("SelectViewerAdapter").w("🖱️ ItemView onClick - viewer=${viewer.name}")
                onViewerClick(viewer)
            }

            // Focus scroll: Focus alındığında item'ı görünür alana getir
            itemView.setOnFocusChangeListener { focusedView, hasFocus ->
                Timber.tag("SelectViewerAdapter").w("👁️ onFocusChange: viewer=${viewer.name}, hasFocus=$hasFocus")
                if (hasFocus) {
                    val recyclerView = focusedView.parent as? RecyclerView
                    Timber.tag("SelectViewerAdapter").w("📦 RecyclerView bulundu: ${recyclerView != null}")
                    if (recyclerView != null) {
                        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                        Timber.tag("SelectViewerAdapter").w("📐 LayoutManager bulundu: ${layoutManager != null}")
                        if (layoutManager != null) {
                            val focusedPosition = recyclerView.getChildAdapterPosition(focusedView)
                            Timber.tag("SelectViewerAdapter").w("📍 Focused position: $focusedPosition")
                            if (focusedPosition != RecyclerView.NO_POSITION) {
                                recyclerView.post {
                                    Timber.tag("SelectViewerAdapter").w("🔄 RecyclerView.post içinde - scroll işlemi başlıyor")
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
