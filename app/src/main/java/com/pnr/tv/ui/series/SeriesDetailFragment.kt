package com.pnr.tv.ui.series

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import coil.load
import coil.size.Scale
import com.google.android.material.tabs.TabLayout
import com.pnr.tv.MainActivity
import com.pnr.tv.PlayerActivity
import com.pnr.tv.R
import com.pnr.tv.repository.UserRepository
import com.pnr.tv.ui.series.model.SeriesSeason
import com.pnr.tv.extensions.normalizeBaseUrl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dizi detay sayfası için Fragment.
 * Dizi bilgilerini, sezonları ve bölümleri gösterir.
 */
@AndroidEntryPoint
class SeriesDetailFragment : Fragment() {
    private lateinit var viewModel: SeriesDetailViewModel
    private lateinit var seriesPoster: ImageView
    private lateinit var seriesTitle: TextView
    private lateinit var seriesRating: TextView
    private lateinit var seriesPlot: TextView
    private lateinit var seriesCreator: TextView
    private lateinit var seriesGenre: TextView
    private lateinit var seriesCast: TextView
    private lateinit var creatorLayout: View
    private lateinit var genreLayout: View
    private lateinit var castLayout: View
    private lateinit var seasonTabLayout: TabLayout
    private lateinit var episodesRecyclerView: RecyclerView
    private lateinit var episodesAdapter: EpisodesAdapter
    private lateinit var loadingIndicator: View
    private lateinit var emptyStateText: TextView
    private lateinit var addFavoriteButton: android.widget.ImageButton

    @Inject
    lateinit var viewModelFactory: SeriesDetailViewModel.Factory

    @Inject
    lateinit var userRepository: UserRepository

    // Focus state kaydetme için
    private var lastFocusedEpisodePosition: Int = -1

    private val playerActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Oynatıcıdan döndükten sonra yapılacak işlemler
        }

    companion object {
        private const val ARG_SERIES_ID = "series_id"

        fun newInstance(seriesId: Int): SeriesDetailFragment {
            return SeriesDetailFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SERIES_ID, seriesId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return viewModelFactory.create() as T
            }
        })[SeriesDetailViewModel::class.java]

        arguments?.getInt(ARG_SERIES_ID)?.let { seriesId ->
            viewModel.loadSeries(seriesId)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_series_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupNavbar(view)
        setupViews(view)
        setupSeasonTabs(view)
        setupEpisodesList(view)
        setupFavoriteButton()
        observeSeries()
        observeTmdbDetails()
        observeLoadingState()
        observeErrorState()
        observeViewerSelectionDialog()
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.hideTopMenu()
        
        // İzlenip geri dönülen bölüme odaklanma
        if (lastFocusedEpisodePosition >= 0 && episodesAdapter.itemCount > lastFocusedEpisodePosition) {
            episodesRecyclerView.post {
                val viewHolder = episodesRecyclerView.findViewHolderForAdapterPosition(lastFocusedEpisodePosition)
                viewHolder?.itemView?.requestFocus()
            }
        }
    }

    private fun setupNavbar(view: View) {
        val navbarView = view.findViewById<View>(R.id.navbar)
        val titleTextView = navbarView.findViewById<TextView>(R.id.txt_navbar_title)
        titleTextView?.text = getString(R.string.page_series_details)

        navbarView.findViewById<View>(R.id.btn_navbar_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val homeButton = navbarView.findViewById<View>(R.id.btn_navbar_home)
        homeButton?.setOnClickListener {
            parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        
        // Home butonundan sağ yön tuşuna basıldığında olayı tüket (focus gitmesin)
        homeButton?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                return@setOnKeyListener true // Olayı tüket
            }
            false
        }
    }

    private fun setupViews(view: View) {
        seriesPoster = view.findViewById(R.id.img_series_poster)
        seriesTitle = view.findViewById(R.id.txt_series_title)
        seriesRating = view.findViewById(R.id.txt_series_rating)
        seriesPlot = view.findViewById(R.id.txt_series_plot)
        seriesCreator = view.findViewById(R.id.txt_series_creator)
        seriesGenre = view.findViewById(R.id.txt_series_genre)
        seriesCast = view.findViewById(R.id.txt_series_cast)
        creatorLayout = view.findViewById(R.id.layout_creator)
        genreLayout = view.findViewById(R.id.layout_genre)
        castLayout = view.findViewById(R.id.layout_cast)
        loadingIndicator = view.findViewById(R.id.loading_indicator)
        emptyStateText = view.findViewById(R.id.txt_empty_state)
        addFavoriteButton = view.findViewById(R.id.btn_add_to_favorites)
    }

    private fun setupSeasonTabs(view: View) {
        seasonTabLayout = view.findViewById(R.id.tab_layout_seasons)

        seasonTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                (tab?.tag as? SeriesSeason)?.let { viewModel.selectSeason(it.seasonNumber) }
                // Seçili tab'ın metnini beyaz yap
                tab?.customView?.let { view ->
                    (view as? TextView)?.setTextColor(android.graphics.Color.WHITE)
                }
                // NOT: Odak isteği episodes.collect bloğunda, layout pass bittikten sonra yapılacak
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // Seçili olmayan tab'ın metnini gri yap
                tab?.customView?.let { view ->
                    (view as? TextView)?.setTextColor(android.graphics.Color.parseColor("#A0A0A0"))
                }
            }
            override fun onTabReselected(tab: TabLayout.Tab?) { }
        })

        // Sezon sekmelerinin yönlendirmeleri
        seasonTabLayout.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        // Yukarı tuşu: Favoriler butonuna odaklan
                        addFavoriteButton.requestFocus()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Aşağı tuşu: Bölümler listesine odaklan
                        if (episodesAdapter.itemCount > 0) {
                            episodesRecyclerView.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        // Sağa git
                        val currentPosition = seasonTabLayout.selectedTabPosition
                        val nextPosition = currentPosition + 1
                        if (nextPosition < seasonTabLayout.tabCount) {
                            seasonTabLayout.getTabAt(nextPosition)?.select()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        // Sola git
                        val currentPosition = seasonTabLayout.selectedTabPosition
                        val prevPosition = currentPosition - 1
                        if (prevPosition >= 0) {
                            seasonTabLayout.getTabAt(prevPosition)?.select()
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.seasons.collect { seasons ->
                    if (seasons.isNotEmpty()) {
                        seasonTabLayout.removeAllTabs()
                        seasons.forEachIndexed { index, season ->
                            // Custom view ile tab oluştur (metin ortalanmış)
                            val tabView = TextView(requireContext()).apply {
                                text = season.name
                                gravity = android.view.Gravity.CENTER
                                textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                                textSize = 14f
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                                setPadding(12, 16, 12, 16)
                                minWidth = 0
                                minHeight = 0
                                isFocusable = true
                                isFocusableInTouchMode = true
                                setTextColor(if (index == 0) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#A0A0A0"))
                            }
                            
                            // TabLayout'un tab container'ı için layoutParams ayarla
                            tabView.layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            
                            val tab = seasonTabLayout.newTab().setCustomView(tabView)
                            tab.tag = season
                            seasonTabLayout.addTab(tab)
                        }
                        
                        // İlk açılışta odağı ayarlama
                        seasonTabLayout.post {
                            if (seasonTabLayout.tabCount > 0) {
                                seasonTabLayout.getTabAt(0)?.customView?.requestFocus()
                            }
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedSeasonNumber.collect { selectedNumber ->
                    if (selectedNumber != null) {
                        // Tüm tab'ların renklerini güncelle
                        for (i in 0 until seasonTabLayout.tabCount) {
                            val tab = seasonTabLayout.getTabAt(i)
                            val isSelected = (tab?.tag as? SeriesSeason)?.seasonNumber == selectedNumber
                            tab?.customView?.let { view ->
                                (view as? TextView)?.setTextColor(
                                    if (isSelected) android.graphics.Color.WHITE 
                                    else android.graphics.Color.parseColor("#A0A0A0")
                                )
                            }
                            
                            if (isSelected && tab?.isSelected == false) {
                                tab.select()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupEpisodesList(view: View) {
        episodesRecyclerView = view.findViewById(R.id.recycler_episodes)
        episodesAdapter = EpisodesAdapter(
            onEpisodeClick = { episode -> playEpisode(episode) },
            onFocusUpToSeasons = { 
                // Bölüm listesinden yukarı çıkış: Seçili sekmeye odaklan
                val selectedTab = seasonTabLayout.getTabAt(seasonTabLayout.selectedTabPosition)
                selectedTab?.customView?.requestFocus()
            }
        )

        // FlexboxLayoutManager ile yan yana kutucuklar
        val flexboxLayoutManager = FlexboxLayoutManager(requireContext()).apply {
            flexDirection = FlexDirection.ROW  // Yatay sıralama
            flexWrap = FlexWrap.WRAP           // Satır dolunca alt satıra geç
            justifyContent = JustifyContent.FLEX_START  // Sola yasla
        }
        
        episodesRecyclerView.layoutManager = flexboxLayoutManager
        episodesRecyclerView.adapter = episodesAdapter
        episodesRecyclerView.itemAnimator = null

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.episodes.collect { episodes ->
                    episodesAdapter.submitList(episodes)
                    
                    // Empty state yönetimi
                    if (episodes.isEmpty() && viewModel.seasons.value.isNotEmpty()) {
                        episodesRecyclerView.visibility = View.GONE
                        emptyStateText.visibility = View.VISIBLE
                        emptyStateText.text = getString(R.string.no_episodes_found)
                    } else {
                        episodesRecyclerView.visibility = View.VISIBLE
                        emptyStateText.visibility = View.GONE
                        
                        // İzlenip geri dönülen bölüme odaklanma
                        if (lastFocusedEpisodePosition >= 0 && lastFocusedEpisodePosition < episodes.size) {
                            episodesRecyclerView.post {
                                val viewHolder = episodesRecyclerView.findViewHolderForAdapterPosition(lastFocusedEpisodePosition)
                                viewHolder?.itemView?.requestFocus()
                            }
                        }
                    }
                    
                    // Bölüm listesi güncellendiğinde, yani layout'un yeniden çizileceği kesinleştiğinde,
                    // bir sonraki frame'de çalışmak üzere odak isteme eylemini sıraya alıyoruz.
                    // Bu, layout pass bittikten sonra odağın güvenli bir şekilde ayarlanmasını sağlar.
                    seasonTabLayout.post {
                        val selectedTabPosition = seasonTabLayout.selectedTabPosition
                        if (selectedTabPosition != -1) {
                            val selectedTab = seasonTabLayout.getTabAt(selectedTabPosition)
                            selectedTab?.customView?.requestFocus()
                        }
                    }
                }
            }
        }
        
        // Bölümler RecyclerView'ında focus değiştiğinde pozisyonu kaydet
        episodesRecyclerView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                view.setOnFocusChangeListener { focusedView, hasFocus ->
                    if (hasFocus) {
                        val position = episodesRecyclerView.getChildAdapterPosition(focusedView)
                        if (position != RecyclerView.NO_POSITION) {
                            lastFocusedEpisodePosition = position
                        }
                    }
                }
            }
            
            override fun onChildViewDetachedFromWindow(view: View) {}
        })
    }

    private fun observeLoadingState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { isLoading ->
                    loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
                    
                    // Loading sırasında diğer bileşenleri gizle
                    if (isLoading) {
                        episodesRecyclerView.visibility = View.GONE
                        emptyStateText.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun observeErrorState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.error.collect { errorMessage ->
                    // Error mesajını AlertDialog ile göster
                    AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.error_title))
                        .setMessage(errorMessage)
                        .setPositiveButton(getString(R.string.retry)) { _, _ ->
                            // Tekrar dene
                            arguments?.getInt(ARG_SERIES_ID)?.let { seriesId ->
                                viewModel.loadSeries(seriesId)
                            }
                        }
                        .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                            dialog.dismiss()
                            parentFragmentManager.popBackStack()
                        }
                        .setCancelable(false)
                        .show()
                }
            }
        }
    }

    private fun observeSeries() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.series.collect { series ->
                    series?.let {
                        seriesTitle.text = it.name ?: ""
                        if (it.rating != null && it.rating > 0) {
                            seriesRating.visibility = View.VISIBLE
                            seriesRating.text = "${String.format("%.1f", it.rating)} / 10"
                        } else {
                            seriesRating.visibility = View.GONE
                        }
                        val imageUrl = it.coverUrl
                        if (!imageUrl.isNullOrBlank()) {
                            seriesPoster.load(imageUrl) {
                                placeholder(R.drawable.live)
                                error(R.drawable.live)
                                crossfade(true)
                                scale(Scale.FILL)
                            }
                        } else {
                            seriesPoster.load(R.drawable.live) { scale(Scale.FILL) }
                        }
                    }
                }
            }
        }
    }

    private fun observeTmdbDetails() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tmdbDetails.collect { tmdbDetails ->
                    val tmdbId = viewModel.series.value?.tmdbId
                    
                    val creator = viewModel.getCreator(tmdbId, tmdbDetails)
                    creatorLayout.visibility = if (!creator.isNullOrBlank()) {
                        seriesCreator.text = creator
                        View.VISIBLE
                    } else View.GONE

                    val genre = viewModel.getGenre(tmdbDetails)
                    genreLayout.visibility = if (!genre.isNullOrBlank()) {
                        seriesGenre.text = genre
                        View.VISIBLE
                    } else View.GONE

                    val cast = viewModel.getCast(tmdbId, tmdbDetails)
                    castLayout.visibility = if (!cast.isNullOrBlank()) {
                        seriesCast.text = cast
                        View.VISIBLE
                    } else View.GONE

                    val overview = viewModel.getOverview(tmdbId, tmdbDetails)
                    seriesPlot.text = overview?.takeIf { it.isNotBlank() } ?: getString(R.string.no_overview)
                }
            }
        }
    }

    private fun setupFavoriteButton() {
        addFavoriteButton.setOnClickListener {
            viewModel.addToFavorites()
        }
        
        // Favoriler butonunun yönlendirmeleri
        addFavoriteButton.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Aşağı tuşu: Seçili sekmeye odaklan
                        val selectedTab = seasonTabLayout.getTabAt(seasonTabLayout.selectedTabPosition)
                        if (selectedTab != null && seasonTabLayout.tabCount > 0) {
                            selectedTab.customView?.requestFocus() ?: seasonTabLayout.getTabAt(0)?.customView?.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        // Sol ve sağ tuşları: Olayı tüket
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
    }

    private fun observeViewerSelectionDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.showViewerSelectionDialog.collect { viewers ->
                    com.pnr.tv.ui.viewers.SelectViewerDialog(requireContext(), viewers) { viewer ->
                        viewModel.saveFavoriteForViewer(viewer)
                        android.widget.Toast.makeText(
                            requireContext(),
                            getString(R.string.toast_favorite_added),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }.show()
                }
            }
        }
    }

    private fun playEpisode(episode: ParsedEpisode) {
        viewLifecycleOwner.lifecycleScope.launch {
            val user = userRepository.currentUser.firstOrNull()
            if (user != null) {
                val baseUrl = user.dns.normalizeBaseUrl()
                val episodeIdInt = episode.episodeId.toIntOrNull()
                if (episodeIdInt != null) {
                    val streamUrl = viewModel.getEpisodeStreamUrl(baseUrl, user.username, user.password, episodeIdInt, episode.containerExtension)
                    timber.log.Timber.d("📺 DİZİ BÖLÜM URL: $streamUrl (extension: ${episode.containerExtension ?: "ts (default)"})")
                    
                    // Kaldığı yerden devam için bölüm ID'sini gönder
                    val contentId = "episode_${episode.episodeId}"
                    
                    // Bölüm başlığı ve dizi IMDB puanı
                    val seriesInfo = viewModel.series.value
                    val episodeTitle = "${seriesInfo?.name ?: ""} - ${episode.title}"
                    val seriesRating = seriesInfo?.rating
                    
                    // Bölümü izlendi olarak işaretle
                    viewModel.markEpisodeAsWatched(episode)
                    
                    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_VIDEO_URL, streamUrl)
                        putExtra(PlayerActivity.EXTRA_CONTENT_ID, contentId)
                        putExtra(PlayerActivity.EXTRA_CONTENT_TITLE, episodeTitle)
                        putExtra(PlayerActivity.EXTRA_CONTENT_RATING, seriesRating ?: -1.0)
                    }
                    playerActivityLauncher.launch(intent)
                } else {
                    timber.log.Timber.e("❌ Episode ID geçersiz: ${episode.episodeId}")
                }
            }
        }
    }
}
