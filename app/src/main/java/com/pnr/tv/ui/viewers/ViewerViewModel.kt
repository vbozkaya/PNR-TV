package com.pnr.tv.ui.viewers

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.pnr.tv.R
import com.pnr.tv.db.entity.ViewerEntity
import com.pnr.tv.repository.ViewerRepository
import com.pnr.tv.core.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ViewerViewModel
    @Inject
    constructor(
        private val viewerRepository: ViewerRepository,
        @ApplicationContext override val context: Context,
    ) : BaseViewModel() {
        fun getAllViewers(): Flow<List<ViewerEntity>> = viewerRepository.getAllViewers()

        fun addViewer(
            name: String,
            isDeletable: Boolean = true,
        ) {
            viewModelScope.launch {
                // userId will be set by repository
                val viewer = ViewerEntity(name = name, userId = 0, isDeletable = isDeletable)
                viewerRepository.addViewer(viewer)
            }
        }

        fun deleteViewer(viewer: ViewerEntity) {
            if (!viewer.isDeletable) {
                showToast(context.getString(R.string.error_cannot_delete_default_viewer))
                return
            }
            viewModelScope.launch {
                viewerRepository.deleteViewer(viewer)
            }
        }
    }
