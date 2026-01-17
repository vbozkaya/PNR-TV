package com.pnr.tv.ui.viewers

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pnr.tv.core.base.BaseActivity
import com.pnr.tv.R
import com.pnr.tv.databinding.ActivityViewersBinding
import com.pnr.tv.databinding.DialogAddViewerBinding
import com.pnr.tv.db.entity.ViewerEntity
import com.pnr.tv.extensions.showCustomToast
import com.pnr.tv.premium.AdManager
import com.pnr.tv.premium.PremiumManager
import com.pnr.tv.util.ViewerInitializer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ViewersActivity : BaseActivity() {
    private lateinit var binding: ActivityViewersBinding
    private val viewModel: ViewerViewModel by viewModels()
    private lateinit var adapter: ViewersAdapter

    @Inject
    lateinit var adManager: AdManager

    @Inject
    lateinit var premiumManager: PremiumManager

    @Inject
    lateinit var viewerInitializer: ViewerInitializer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupAddButton()
        observeViewers()
        observeToastEvents()

        // Banner reklamı setup et
        setupBannerAd(adManager, premiumManager)

        // Varsayılan izleyiciyi kontrol et ve yoksa oluştur
        lifecycleScope.launch {
            viewerInitializer.initializeIfNeeded()
        }
    }

    override fun onStart() {
        super.onStart()
        // Activity açıldığında Home butonuna focus ver
        binding.root.post {
            val navbar = binding.root.findViewById<View>(R.id.navbar)
            val homeButton = navbar?.findViewById<View>(R.id.btn_navbar_home)
            homeButton?.requestFocus()
        }
    }

    override fun getNavbarTitle(): String? {
        return getString(R.string.page_viewers)
    }

    private fun setupRecyclerView() {
        adapter =
            ViewersAdapter(
                onDeleteClick = { viewer ->
                    showDeleteDialog(viewer)
                },
            )
        binding.recyclerViewers.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.recyclerViewers.adapter = adapter
    }

    private fun setupAddButton() {
        binding.btnAddViewer.setOnClickListener {
            showAddViewerDialog()
        }
    }

    private var previousViewerCount = 0

    private fun observeViewers() {
        lifecycleScope.launch {
            viewModel.getAllViewers().collectLatest { viewers ->
                val wasViewerAdded = viewers.size > previousViewerCount
                previousViewerCount = viewers.size

                adapter.submitList(viewers)
                binding.emptyView.visibility = if (viewers.isEmpty()) View.VISIBLE else View.GONE

                binding.recyclerViewers.post {
                    if (wasViewerAdded && viewers.isNotEmpty()) {
                        // Yeni izleyici eklendiğinde son item'ın sil butonuna focus ver
                        // Ancak varsayılan izleyiciye (isDeletable=false) focus verme
                        val lastPosition = viewers.size - 1
                        val lastViewer = viewers[lastPosition]
                        if (lastViewer.isDeletable) {
                            val lastItem = binding.recyclerViewers.findViewHolderForAdapterPosition(lastPosition)
                            lastItem?.itemView?.findViewById<android.widget.Button>(R.id.btn_delete)?.requestFocus()
                        } else {
                            // Varsayılan izleyici eklendiyse, ilk silinebilir izleyicinin sil butonuna focus ver
                            val firstDeletableViewer = viewers.find { it.isDeletable }
                            if (firstDeletableViewer != null) {
                                val firstDeletablePosition = viewers.indexOf(firstDeletableViewer)
                                val firstDeletableItem = binding.recyclerViewers.findViewHolderForAdapterPosition(firstDeletablePosition)
                                firstDeletableItem?.itemView?.findViewById<android.widget.Button>(R.id.btn_delete)?.requestFocus()
                            } else {
                                // Hiç silinebilir izleyici yoksa "İzleyici Ekle" butonuna focus ver
                                binding.btnAddViewer.requestFocus()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeToastEvents() {
        lifecycleScope.launch {
            viewModel.toastEvent.collectLatest { message ->
                showCustomToast(message)
            }
        }
    }

    private fun showAddViewerDialog() {
        val dialogBinding = DialogAddViewerBinding.inflate(layoutInflater)
        val dialog =
            AlertDialog.Builder(this)
                .setView(dialogBinding.root)
                .setCancelable(true)
                .create()

        // Dialog window ayarları
        val window = dialog.window
        // Dialog'u ortala ve sabit genişlik kullan (CardView kendi genişliğini kullanacak)
        window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        // Arka planı şeffaf yap (CardView kendi arka planını kullanacak)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Butonları ekle
        dialogBinding.btnDialogSave.setOnClickListener {
            val name = dialogBinding.etViewerName.text.toString().trim()
            if (name.isNotEmpty()) {
                viewModel.addViewer(name)
                showCustomToast(getString(R.string.toast_viewer_added))
                dialog.dismiss()
            } else {
                showCustomToast(getString(R.string.error_fill_fields))
            }
        }

        dialogBinding.btnDialogCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialogBinding.etViewerName.requestFocus()
    }

    private fun showDeleteDialog(viewer: ViewerEntity) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm, null)
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)

        val window = dialog.window
        window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val titleTextView = view.findViewById<android.widget.TextView>(R.id.tv_dialog_title)
        val messageTextView = view.findViewById<android.widget.TextView>(R.id.tv_dialog_message)
        val btnYes = view.findViewById<android.widget.TextView>(R.id.btn_dialog_yes)
        val btnNo = view.findViewById<android.widget.TextView>(R.id.btn_dialog_no)

        titleTextView.text = getString(R.string.dialog_delete_viewer_title)
        messageTextView.text = getString(R.string.dialog_delete_viewer_message, viewer.name)

        btnYes.setOnClickListener {
            viewModel.deleteViewer(viewer)
            dialog.dismiss()
        }

        btnNo.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        // Güvenlik için "Hayır" butonuna focus ver
        btnNo.requestFocus()
    }
}
