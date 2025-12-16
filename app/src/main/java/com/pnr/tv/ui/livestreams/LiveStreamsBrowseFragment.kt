package com.pnr.tv.ui.livestreams

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pnr.tv.Constants
import com.pnr.tv.MainViewModel
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
    private val mainViewModel: MainViewModel by activityViewModels()

    // BaseBrowseFragment requires BaseViewModel
    override val viewModel: com.pnr.tv.ui.base.BaseViewModel
        get() = mainViewModel

    private val playerActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

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
        observeErrorState()
        observeLoadingState()
    }

    private fun observeErrorState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.liveStreamsErrorMessage.collect { errorMsg ->
                    if (errorMsg != null) {
                        showErrorState(errorMsg)
                    }
                }
            }
        }
    }

    private fun observeLoadingState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.isLiveStreamsLoading.collect { isLoading ->
                    if (isLoading) {
                        showLoadingState()
                    } else if (mainViewModel.liveStreamsErrorMessage.value == null) {
                        showContentState()
                    }
                }
            }
        }
    }

    // Abstract properties from BaseBrowseFragment
    override val categoriesFlow: Flow<List<CategoryItem>>
        get() =
            mainViewModel.liveStreamCategories.distinctUntilChanged { old, new ->
                old.size == new.size && old.map { it.categoryId } == new.map { it.categoryId }
            }

    override val contentsFlow: Flow<List<ContentItem>>
        get() = mainViewModel.liveStreams.map { it }

    override val selectedCategoryIdFlow: Flow<String?>
        get() = mainViewModel.selectedLiveStreamCategoryId

    override val toastEventFlow: Flow<String>
        get() = mainViewModel.toastEvent

    // Abstract methods from BaseBrowseFragment
    override fun getNavbarTitle(): String = getString(R.string.page_live_streams)

    override fun getCategoriesRecyclerViewId(): Int = R.id.recycler_categories

    override fun getContentRecyclerViewId(): Int = R.id.recycler_channels

    override fun getEmptyStateTextViewId(): Int = R.id.txt_empty_state

    override fun getGridColumnCount(): Int = Constants.GRID_COLUMN_COUNT

    // Override hooks from BaseBrowseFragment
    override fun onInitialLoad() {
        mainViewModel.loadLiveStreamsIfNeeded()
    }

    override fun onCategoryClicked(category: CategoryItem) {
        mainViewModel.selectLiveStreamCategory(category.categoryId)
    }

    override fun selectCategoryById(categoryId: String?) {
        // Kategori ID'sine göre kategori seç
        categoryId?.let { id ->
            mainViewModel.selectLiveStreamCategory(id)
        }
    }

    override fun onCategoryFocused(category: CategoryItem) {
        // Focus geldiğinde kategoriyi seç ve içerikleri yükle
        mainViewModel.selectLiveStreamCategory(category.categoryId)
    }

    override fun onContentClicked(item: ContentItem) {
        // Cast to LiveStreamEntity and open player
        if (item is LiveStreamEntity) {
            // Son seçili kategoriyi ve odaklanılan pozisyonu kaydet
            val position = contentAdapter.currentList.indexOf(item)
            if (position != -1) {
                mainViewModel.lastFocusedContentPosition = position
                // Mevcut seçili kategoriyi kaydet
                mainViewModel.lastSelectedCategoryId = mainViewModel.selectedLiveStreamCategoryId.value
            }

            mainViewModel.onChannelSelected(item)
        }
    }

    override fun onContentLongPressed(item: ContentItem) {
        // Long press: toggle favorite
        if (item is LiveStreamEntity) {
            viewLifecycleOwner.lifecycleScope.launch {
                val isFavorite = mainViewModel.isLiveStreamFavorite(item.streamId).first()
                if (isFavorite) {
                    mainViewModel.removeLiveStreamFavorite(item.streamId)
                } else {
                    mainViewModel.addLiveStreamFavorite(item.streamId)
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
                categoryIdInt == MainViewModel.VIRTUAL_CATEGORY_ID_FAVORITES ||
                    categoryIdInt == MainViewModel.VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED
            ) &&
            channels.isEmpty()
        ) {
            val message =
                when (categoryIdInt) {
                    MainViewModel.VIRTUAL_CATEGORY_ID_FAVORITES -> {
                        getString(R.string.empty_favorites)
                    }
                    MainViewModel.VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED -> {
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
                mainViewModel.openPlayerEvent.collect { (url, channelId, categoryId) ->
                    val intent =
                        Intent(requireContext(), PlayerActivity::class.java).apply {
                            putExtra(PlayerActivity.EXTRA_VIDEO_URL, url)
                            putExtra(PlayerActivity.EXTRA_CHANNEL_ID, channelId)
                            // Kategori ID'sini ekle (kanal değişimi için)
                            categoryId?.let {
                                putExtra(PlayerActivity.EXTRA_CATEGORY_ID, it)
                            }
                        }
                    playerActivityLauncher.launch(intent)
                }
            }
        }
    }

    companion object {
        private const val KEY_IS_INITIAL_LAUNCH = "is_initial_launch"

        /**
         * Yeni bir LiveStreamsBrowseFragment örneği oluşturur.
         *
         * @param isInitialLaunch Ana menüden ilk kez açılıyor mu
         */
        fun newInstance(isInitialLaunch: Boolean = false): LiveStreamsBrowseFragment {
            return LiveStreamsBrowseFragment().apply {
                arguments =
                    Bundle().apply {
                        putBoolean(KEY_IS_INITIAL_LAUNCH, isInitialLaunch)
                    }
            }
        }
    }
}
