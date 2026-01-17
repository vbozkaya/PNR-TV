package com.pnr.tv.ui.livestreams

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pnr.tv.R
import com.pnr.tv.core.constants.UIConstants
import com.pnr.tv.db.entity.LiveStreamEntity
import com.pnr.tv.model.CategoryItem
import com.pnr.tv.model.ContentItem
import com.pnr.tv.core.base.BaseBrowseFragment
import com.pnr.tv.ui.player.PlayerActivity
import com.pnr.tv.ui.player.handler.PlayerIntentHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Canlı yayınlar için Fragment.
 * BaseBrowseFragment'tan türetilmiştir ve tüm ortak mantık base'de yönetilir.
 */
@AndroidEntryPoint
class LiveStreamsBrowseFragment : BaseBrowseFragment() {
    private val liveStreamViewModel: LiveStreamViewModel by activityViewModels()

    @javax.inject.Inject
    lateinit var contentRepository: com.pnr.tv.repository.ContentRepository

    // BaseBrowseFragment requires BaseViewModel
    override val viewModel: com.pnr.tv.core.base.BaseViewModel
        get() = liveStreamViewModel

    private val playerActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sadece ilk oluşturulduğunda yükle, savedInstanceState null ise
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                // Veri kontrolü yap, eğer veri yoksa uyarı göster ve çık
                if (checkDataAndShowWarningIfNeeded()) {
                    onInitialLoad()
                }
            }
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

    override fun onResume() {
        super.onResume()
        // Reaktif yapıya güven: Veritabanı (Room) ve Flow yapısı zaten kullanılıyor.
        // Veri değişirse UI otomatik güncellenir. Manuel bir API isteğine onResume içinde gerek yok.
        // Yükleme işlemi sadece Fragment ilk oluşturulduğunda (onCreate / onInitialLoad) yapılır.
        // Kullanıcı sadece Ana Sayfadan manuel "Güncelle" dediğinde veriler yenilenir.
    }

    // Note: createAdapters() metodunu BrowseSetupDelegate yönetiyor artık
    // Eğer özel adapter özelleştirmesi gerekiyorsa, initializeViews() sonrasında yapılabilir

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

    override fun getCategoriesRecyclerViewId(): Int = R.id.categories_recycler_view

    override fun getContentRecyclerViewId(): Int = R.id.recycler_channels

    override fun getEmptyStateTextViewId(): Int = R.id.txt_empty_state

    override fun getGridColumnCount(): Int = UIConstants.GRID_COLUMN_COUNT

    /**
     * Canlı Kanallar sayfasında arama çubuğu gösterilir, filtre butonu gizlenir
     * Arama çubuğu premium kontrolü altındadır
     */
    override fun setupFilterButton() {
        super.setupFilterButton()
        val filterButton = navbarView?.findViewById<View>(R.id.btn_navbar_filter)
        val searchEditText = navbarView?.findViewById<android.widget.EditText>(R.id.edt_navbar_search)

        // Filtre butonunu gizle
        filterButton?.visibility = View.GONE

        // Arama kutusu ayarları
        searchEditText?.visibility = View.VISIBLE
        searchEditText?.isFocusable = true
        searchEditText?.isFocusableInTouchMode = true // TV remote için gerekli

        // Metin değişikliklerini dinle - premium durumuna göre
        viewLifecycleOwner.lifecycleScope.launch {
            premiumManager.isPremium().collectLatest { isPremium ->
                if (isPremium) {
                    // Premium ise TextWatcher ekle
                    searchEditText?.addTextChangedListener(
                        object : TextWatcher {
                            override fun beforeTextChanged(
                                s: CharSequence?,
                                start: Int,
                                count: Int,
                                after: Int,
                            ) {}

                            override fun onTextChanged(
                                s: CharSequence?,
                                start: Int,
                                before: Int,
                                count: Int,
                            ) {
                                liveStreamViewModel.onLiveStreamSearchQueryChanged(s?.toString() ?: "")
                            }

                            override fun afterTextChanged(s: Editable?) {}
                        },
                    )
                } else {
                    // Premium değilse TextWatcher'ı kaldır ve metni temizle
                    searchEditText?.text?.clear()
                    searchEditText?.removeTextChangedListener(null)
                }
            }
        }
    }

    // Override hooks from BaseBrowseFragment
    override suspend fun hasData(): Boolean {
        return try {
            contentRepository.hasLiveStreams()
        } catch (e: Exception) {
            Timber.e(e, "Canlı yayın veri kontrolü hatası")
            false
        }
    }

    override fun onInitialLoad() {
        liveStreamViewModel.loadLiveStreamsIfNeeded()
    }

    override fun onCategoryClicked(item: CategoryItem) {
        // Arama çubuğunu temizle (Kategoriye geçiş yapıldığında arama modundan çıkmak için)
        val searchEditText = navbarView?.findViewById<android.widget.EditText>(R.id.edt_navbar_search)
        if (!searchEditText?.text.isNullOrEmpty()) {
            searchEditText?.text?.clear()
        }

        liveStreamViewModel.selectLiveStreamCategory(item.categoryId)
    }

    override fun selectCategoryById(categoryId: String?) {
        // Kategori ID'sine göre kategori seç
        categoryId?.let { id ->
            liveStreamViewModel.selectLiveStreamCategory(id)
        }
    }

    override fun onCategoryFocused(item: CategoryItem) {
        // Sadece focus değişikliği - içerik yükleme YOK
        // İçerik yükleme sadece OK tuşuna basıldığında (onCategoryClicked) yapılır
        // Base sınıfın onCategoryFocused metodunu çağır (pendingRestorePosition temizlemek için)
        super.onCategoryFocused(item)

        // NOT: Kategori seçimi yapılmıyor - sadece focus değişikliği
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
        // Long press özelliği kullanılmıyor
    }

    override fun updateEmptyState(
        contents: List<ContentItem>,
        selectedCategoryId: String?,
    ) {
        val channels = contents.filterIsInstance<LiveStreamEntity>()
        if (channels.isEmpty()) {
            showEmptyState(getString(R.string.empty_live_streams))
        } else {
            showContentState()
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
                            putExtra(PlayerIntentHandler.EXTRA_VIDEO_URL, url)
                            putExtra(PlayerIntentHandler.EXTRA_CHANNEL_ID, channelId)
                            // Kategori ID'sini ekle (kanal değişimi için)
                            categoryId?.let {
                                putExtra(PlayerIntentHandler.EXTRA_CATEGORY_ID, it)
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
