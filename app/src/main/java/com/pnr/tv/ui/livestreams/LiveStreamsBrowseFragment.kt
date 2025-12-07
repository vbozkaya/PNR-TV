package com.pnr.tv.ui.livestreams

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pnr.tv.Constants
import com.pnr.tv.PlayerActivity
import com.pnr.tv.R
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.ui.base.BaseBrowseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Canlı yayınlar için Fragment.
 * BaseBrowseFragment'tan türetilmiştir ve tüm ortak mantık base'de yönetilir.
 */
@AndroidEntryPoint
class LiveStreamsBrowseFragment : BaseBrowseFragment() {
    private val viewModel: LiveStreamsViewModel by viewModels()

    private val playerActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val channelId = result.data?.getIntExtra(PlayerActivity.RESULT_CHANNEL_ID, -1)
                if (channelId != null && channelId != -1) {
                    // channelId'yi bul ve o pozisyona focus ver
                    viewLifecycleOwner.lifecycleScope.launch {
                        val contents = contentsFlow.first()
                        val position = contents.indexOfFirst { it.id == channelId }
                        if (position >= 0) {
                            contentRecyclerView.post {
                                val viewHolder = contentRecyclerView.findViewHolderForAdapterPosition(position)
                                viewHolder?.itemView?.requestFocus()
                            }
                        }
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sadece ilk oluşturulduğunda yükle, savedInstanceState null ise
        if (savedInstanceState == null) {
            onInitialLoad()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_live_streams_browse, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        // BaseBrowseFragment handles all setup via initializeViews
        initializeViews(view)
        setupPlayerNavigation()
    }

    // Abstract properties from BaseBrowseFragment
    override val categoriesFlow: Flow<List<CategoryItem>>
        get() =
            viewModel.categories.distinctUntilChanged { old, new ->
                old.size == new.size && old.map { it.categoryId } == new.map { it.categoryId }
            }

    override val contentsFlow: Flow<List<ContentItem>>
        get() = viewModel.channels.map { it }

    override val selectedCategoryIdFlow: Flow<String?>
        get() = viewModel.selectedCategoryId

    override val toastEventFlow: Flow<String>
        get() = viewModel.toastEvent

    // Abstract methods from BaseBrowseFragment
    override fun getNavbarTitle(): String = getString(R.string.page_live_streams)

    override fun getCategoriesRecyclerViewId(): Int = R.id.recycler_categories

    override fun getContentRecyclerViewId(): Int = R.id.recycler_channels

    override fun getEmptyStateTextViewId(): Int = R.id.txt_empty_state

    override fun getGridColumnCount(): Int = Constants.GRID_COLUMN_COUNT

    // Override hooks from BaseBrowseFragment
    override fun onInitialLoad() {
        viewModel.loadContent()
    }

    override fun onCategoryClicked(category: CategoryItem) {
        viewModel.selectCategory(category.categoryId)
    }

    override fun onCategoryFocused(category: CategoryItem) {
        // Focus aldığında otomatik olarak kategori seç
        viewModel.selectCategory(category.categoryId)
    }

    override fun onContentClicked(item: ContentItem) {
        // Cast to LiveStreamEntity and open player
        if (item is LiveStreamEntity) {
            viewModel.onChannelSelected(item)
        }
    }

    override fun onContentLongPressed(item: ContentItem) {
        // Long press: toggle favorite
        if (item is LiveStreamEntity) {
            viewLifecycleOwner.lifecycleScope.launch {
                val isFavorite = viewModel.isFavorite(item.streamId).first()
                if (isFavorite) {
                    viewModel.removeFavorite(item.streamId)
                } else {
                    viewModel.addFavorite(item.streamId)
                }
            }
        }
    }

    override fun updateEmptyState(
        contents: List<ContentItem>,
        selectedCategoryId: String?,
    ) {
        val channels = contents.filterIsInstance<LiveStreamEntity>()
        val categoryIdInt = selectedCategoryId?.toIntOrNull()
        if (categoryIdInt != null &&
            (
                categoryIdInt == LiveStreamsViewModel.VIRTUAL_CATEGORY_ID_FAVORITES ||
                    categoryIdInt == LiveStreamsViewModel.VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED
            ) &&
            channels.isEmpty()
        ) {
            val message =
                when (categoryIdInt) {
                    LiveStreamsViewModel.VIRTUAL_CATEGORY_ID_FAVORITES -> {
                        getString(R.string.empty_favorites)
                    }
                    LiveStreamsViewModel.VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED -> {
                        getString(R.string.empty_recently_watched)
                    }
                    else -> ""
                }
            showEmptyState(message)
        } else {
            super.updateEmptyState(contents, selectedCategoryId)
        }
    }

    /**
     * ViewModel'den gelen player açma event'ini dinler ve PlayerActivity'yi başlatır.
     */
    private fun setupPlayerNavigation() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.openPlayerEvent.collect { (url, channelId) ->
                    val intent =
                        Intent(requireContext(), PlayerActivity::class.java).apply {
                            putExtra(PlayerActivity.EXTRA_VIDEO_URL, url)
                            putExtra(PlayerActivity.EXTRA_CHANNEL_ID, channelId)
                        }
                    playerActivityLauncher.launch(intent)
                }
            }
        }
    }

    companion object {
        /**
         * Yeni bir LiveStreamsBrowseFragment örneği oluşturur.
         */
        fun newInstance(): LiveStreamsBrowseFragment {
            return LiveStreamsBrowseFragment()
        }
    }
}
