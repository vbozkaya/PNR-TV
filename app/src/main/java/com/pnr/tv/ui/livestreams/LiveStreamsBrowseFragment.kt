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
import com.pnr.tv.PlayerActivity
import com.pnr.tv.R
import com.pnr.tv.UIConstants
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.ui.base.BaseBrowseFragment
import com.pnr.tv.ui.browse.ContentAdapter
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
    private val liveStreamViewModel: LiveStreamViewModel by activityViewModels()

    // BaseBrowseFragment requires BaseViewModel
    override val viewModel: com.pnr.tv.ui.base.BaseViewModel
        get() = liveStreamViewModel

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

    /**
     * Adapter'ları oluşturur ve OK tuşu long press desteği ekler.
     */
    override fun createAdapters() {
        // Önce base adapter'ları oluştur
        super.createAdapters()

        // ContentAdapter'ı OK tuşu long press desteği ile yeniden oluştur
        contentAdapter =
            ContentAdapter(
                onContentClick = { content ->
                    onContentClicked(content)
                },
                onContentLongPress = { content ->
                    onContentLongPressed(content)
                },
                gridColumnCount = getGridColumnCount(),
                onOkButtonLongPress = { content ->
                    // OK tuşuna 3 saniye basılı tutulduğunda favori işlemi yap
                    toggleFavorite(content)
                },
            )

        // RecyclerView'a yeni adapter'ı set et
        contentRecyclerView.adapter = contentAdapter
    }

    private fun observeErrorState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                liveStreamViewModel.liveStreamsErrorMessage.collect { errorMsg ->
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
                liveStreamViewModel.isLiveStreamsLoading.collect { isLoading ->
                    if (isLoading) {
                        showLoadingState()
                    } else if (liveStreamViewModel.liveStreamsErrorMessage.value == null) {
                        showContentState()
                    }
                }
            }
        }
    }

    // Abstract properties from BaseBrowseFragment
    override val categoriesFlow: Flow<List<CategoryItem>>
        get() =
            liveStreamViewModel.liveStreamCategories.distinctUntilChanged { old, new ->
                old.size == new.size && old.map { it.categoryId } == new.map { it.categoryId }
            }

    override val contentsFlow: Flow<List<ContentItem>>
        get() = liveStreamViewModel.liveStreams.map { it }

    override val selectedCategoryIdFlow: Flow<String?>
        get() = liveStreamViewModel.selectedLiveStreamCategoryId

    override val toastEventFlow: Flow<String>
        get() = liveStreamViewModel.toastEvent

    // Abstract methods from BaseBrowseFragment
    override fun getNavbarTitle(): String = getString(R.string.page_live_streams)

    override fun getCategoriesRecyclerViewId(): Int = R.id.recycler_categories

    override fun getContentRecyclerViewId(): Int = R.id.recycler_channels

    override fun getEmptyStateTextViewId(): Int = R.id.txt_empty_state

    override fun getGridColumnCount(): Int = UIConstants.GRID_COLUMN_COUNT

    // Override hooks from BaseBrowseFragment
    override fun onInitialLoad() {
        liveStreamViewModel.loadLiveStreamsIfNeeded()
    }

    override fun onCategoryClicked(category: CategoryItem) {
        liveStreamViewModel.selectLiveStreamCategory(category.categoryId)
    }

    override fun selectCategoryById(categoryId: String?) {
        // Kategori ID'sine göre kategori seç
        categoryId?.let { id ->
            liveStreamViewModel.selectLiveStreamCategory(id)
        }
    }

    override fun onCategoryFocused(category: CategoryItem) {
        // Focus geldiğinde kategoriyi seç ve içerikleri yükle
        val startTime = System.currentTimeMillis()
        timber.log.Timber.tag("GRID_UPDATE").d("🎯 Kategori focus: ${category.categoryName} (ID: ${category.categoryId})")
        liveStreamViewModel.selectLiveStreamCategory(category.categoryId)
        val focusTime = System.currentTimeMillis() - startTime
        timber.log.Timber.tag("GRID_UPDATE").d("⚡ Kategori focus süresi: ${focusTime}ms")
    }

    override fun onContentClicked(item: ContentItem) {
        // Cast to LiveStreamEntity and open player
        if (item is LiveStreamEntity) {
            // Son seçili kategoriyi ve odaklanılan pozisyonu kaydet
            val position = contentAdapter.currentList.indexOf(item)
            if (position != -1) {
                liveStreamViewModel.lastFocusedContentPosition = position
                // Mevcut seçili kategoriyi kaydet
                liveStreamViewModel.lastSelectedCategoryId = liveStreamViewModel.selectedLiveStreamCategoryId.value
            }

            liveStreamViewModel.onChannelSelected(item)
        }
    }

    override fun onContentLongPressed(item: ContentItem) {
        // Long press: toggle favorite (for touch/long press events)
        toggleFavorite(item)
    }

    /**
     * Favori durumunu değiştirir.
     */
    private fun toggleFavorite(item: ContentItem) {
        if (item is LiveStreamEntity) {
            viewLifecycleOwner.lifecycleScope.launch {
                val isFavorite = liveStreamViewModel.isLiveStreamFavorite(item.streamId).first()
                if (isFavorite) {
                    liveStreamViewModel.removeLiveStreamFavorite(item.streamId)
                } else {
                    liveStreamViewModel.addLiveStreamFavorite(item.streamId)
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
                categoryIdInt == LiveStreamViewModel.VIRTUAL_CATEGORY_ID_FAVORITES ||
                    categoryIdInt == LiveStreamViewModel.VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED
            ) &&
            channels.isEmpty()
        ) {
            val message =
                when (categoryIdInt) {
                    LiveStreamViewModel.VIRTUAL_CATEGORY_ID_FAVORITES -> {
                        getString(R.string.empty_favorites)
                    }
                    LiveStreamViewModel.VIRTUAL_CATEGORY_ID_RECENTLY_WATCHED -> {
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
                liveStreamViewModel.openPlayerEvent.collect { (url, channelId, categoryId) ->
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
