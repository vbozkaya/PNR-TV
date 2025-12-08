package com.pnr.tv.ui.viewers

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pnr.tv.BaseActivity
import com.pnr.tv.R
import com.pnr.tv.databinding.ActivityViewersBinding
import com.pnr.tv.databinding.DialogAddViewerBinding
import com.pnr.tv.db.entity.ViewerEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ViewersActivity : BaseActivity() {
    private lateinit var binding: ActivityViewersBinding
    private val viewModel: ViewerViewModel by viewModels()
    private lateinit var adapter: ViewersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupAddButton()
        observeViewers()
        observeToastEvents()
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
                        val lastPosition = viewers.size - 1
                        val lastItem = binding.recyclerViewers.findViewHolderForAdapterPosition(lastPosition)
                        lastItem?.itemView?.findViewById<android.widget.Button>(R.id.btn_delete)?.requestFocus()
                    }
                }
            }
        }
    }

    private fun observeToastEvents() {
        lifecycleScope.launch {
            viewModel.toastEvent.collectLatest { message ->
                Toast.makeText(this@ViewersActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddViewerDialog() {
        val dialogBinding = DialogAddViewerBinding.inflate(layoutInflater)
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(R.string.btn_add_viewer)
                .setView(dialogBinding.root)
                .setPositiveButton(R.string.btn_save) { _, _ ->
                    val name = dialogBinding.etViewerName.text.toString().trim()
                    if (name.isNotEmpty()) {
                        viewModel.addViewer(name)
                        Toast.makeText(this, getString(R.string.toast_viewer_added), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, getString(R.string.error_fill_fields), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.btn_exit, null)
                .create()

        dialog.show()
        dialogBinding.etViewerName.requestFocus()
    }

    private fun showDeleteDialog(viewer: ViewerEntity) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_viewer_title)
            .setMessage(getString(R.string.dialog_delete_viewer_message, viewer.name))
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                viewModel.deleteViewer(viewer)
            }
            .setNegativeButton(R.string.dialog_no, null)
            .create()
        
        dialog.show()
        // Güvenlik için "Hayır" butonuna focus ver
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
    }
}



