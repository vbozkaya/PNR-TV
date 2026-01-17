package com.pnr.tv.ui.series

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.tabs.TabLayout
import com.pnr.tv.R
import com.pnr.tv.model.SeriesSeason
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * SeriesDetailFragment için sezon sekmeleri ve bölüm listesi yönetimini yapan handler sınıfı.
 *
 * Bu sınıf, fragment içindeki sezon sekmeleri ve bölüm listesi kurulumu ve state yönetimi mantığını
 * dışarı çıkararak fragment'ın sorumluluklarını azaltır.
 *
 * Sorumlulukları:
 * - Sezon sekmelerinin kurulumu (setupSeasonTabs)
 * - Bölüm listesinin kurulumu (setupEpisodesList)
 * - Focus yönetimi (Android TV için kritik)
 * - StateFlow gözlemleri (seasons, episodes, selectedSeasonNumber)
 */
class SeriesDetailListHandler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        // View references (nullable for safety)
        private var seasonTabLayout: TabLayout? = null
        private var episodesRecyclerView: RecyclerView? = null
        private var episodesAdapter: EpisodesAdapter? = null

        // Focus state kaydetme için
        private var lastFocusedEpisodePosition: Int = -1

        // Callbacks
        private var onSeasonSelected: ((Int) -> Unit)? = null
        private var onEpisodeClicked: ((ParsedEpisode) -> Unit)? = null
        private var onFocusUpToSeasons: (() -> Unit)? = null

        // View references for empty state
        private var emptyStateContainer: View? = null
        private var emptyStateText: TextView? = null

        /**
         * Handler'ı başlatır ve view referanslarını ayarlar.
         *
         * @param view Fragment'ın root view'ı
         * @param onSeasonSelected Sezon seçildiğinde çağrılacak callback
         * @param onEpisodeClicked Bölüm tıklandığında çağrılacak callback
         * @param emptyStateContainer Empty state container view (nullable)
         * @param emptyStateText Empty state text view (nullable)
         */
        fun setup(
            view: View,
            onSeasonSelected: (Int) -> Unit,
            onEpisodeClicked: (ParsedEpisode) -> Unit,
            emptyStateContainer: View? = null,
            emptyStateText: TextView? = null,
        ) {
            this.onSeasonSelected = onSeasonSelected
            this.onEpisodeClicked = onEpisodeClicked
            this.emptyStateContainer = emptyStateContainer
            this.emptyStateText = emptyStateText

            setupSeasonTabs(view)
            setupEpisodesList(view)
        }

        /**
         * Sezon sekmelerini kurar ve listener'ları ayarlar.
         */
        private fun setupSeasonTabs(view: View) {
            seasonTabLayout = view.findViewById(R.id.tab_layout_seasons)

            seasonTabLayout?.addOnTabSelectedListener(
                object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab?) {
                        (tab?.tag as? SeriesSeason)?.let { season ->
                            onSeasonSelected?.invoke(season.seasonNumber)
                        }
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

                    override fun onTabReselected(tab: TabLayout.Tab?) {}
                },
            )

            // Sezon sekmelerinin yönlendirmeleri
            seasonTabLayout?.setOnKeyListener { _, keyCode, event ->
                // Geri tuşu olayını sisteme ilet (return false)
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    return@setOnKeyListener false
                }

                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            // Yukarı tuşu: Favoriler butonuna odaklan (callback ile fragment'a bildir)
                            // Fragment'tan favori butonuna erişim gerekiyor, bu yüzden callback kullanıyoruz
                            // Şimdilik episodesRecyclerView'a odaklanmayı deneyelim
                            return@setOnKeyListener false // Fragment'ta handle edilecek
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            // Aşağı tuşu: Bölümler listesine odaklan
                            episodesAdapter?.let { adapter ->
                                if (adapter.itemCount > 0) {
                                    episodesRecyclerView?.requestFocus()
                                    return@setOnKeyListener true
                                }
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            // Sağa git
                            seasonTabLayout?.let { tabLayout ->
                                val currentPosition = tabLayout.selectedTabPosition
                                val nextPosition = currentPosition + 1
                                if (nextPosition < tabLayout.tabCount) {
                                    tabLayout.getTabAt(nextPosition)?.select()
                                    return@setOnKeyListener true
                                }
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            // Sola git
                            seasonTabLayout?.let { tabLayout ->
                                val currentPosition = tabLayout.selectedTabPosition
                                val prevPosition = currentPosition - 1
                                if (prevPosition >= 0) {
                                    tabLayout.getTabAt(prevPosition)?.select()
                                    return@setOnKeyListener true
                                }
                            }
                        }
                    }
                }
                false
            }
        }

        /**
         * Bölüm listesini kurar ve adapter'ı ayarlar.
         */
        private fun setupEpisodesList(view: View) {
            episodesRecyclerView = view.findViewById(R.id.recycler_episodes)
            episodesAdapter =
                EpisodesAdapter(
                    onEpisodeClick = { episode -> onEpisodeClicked?.invoke(episode) },
                    onFocusUpToSeasons = {
                        // Bölüm listesinden yukarı çıkış: Seçili sekmeye odaklan
                        seasonTabLayout?.let { tabLayout ->
                            val selectedTab = tabLayout.getTabAt(tabLayout.selectedTabPosition)
                            selectedTab?.customView?.requestFocus()
                        }
                    },
                )

            episodesRecyclerView?.let { recyclerView ->
                // FlexboxLayoutManager ile yan yana kutucuklar
                val flexboxLayoutManager =
                    FlexboxLayoutManager(context).apply {
                        flexDirection = FlexDirection.ROW // Yatay sıralama
                        flexWrap = FlexWrap.WRAP // Satır dolunca alt satıra geç
                        justifyContent = JustifyContent.FLEX_START // Sola yasla
                    }

                recyclerView.layoutManager = flexboxLayoutManager
                recyclerView.adapter = episodesAdapter
                recyclerView.itemAnimator = null

                // Bölümler RecyclerView'ında focus değiştiğinde pozisyonu kaydet
                recyclerView.addOnChildAttachStateChangeListener(
                object : RecyclerView.OnChildAttachStateChangeListener {
                    override fun onChildViewAttachedToWindow(view: View) {
                        view.setOnFocusChangeListener { focusedView, hasFocus ->
                            if (hasFocus) {
                                episodesRecyclerView?.let { rv ->
                                    val position = rv.getChildAdapterPosition(focusedView)
                                    if (position != RecyclerView.NO_POSITION) {
                                        lastFocusedEpisodePosition = position
                                    }
                                }
                            }
                        }
                    }

                    override fun onChildViewDetachedFromWindow(view: View) {}
                },
                )
            }
        }

        /**
         * Sezonlar StateFlow'sunu gözlemler ve sekmeleri günceller.
         */
        fun observeSeasons(
            lifecycleOwner: LifecycleOwner,
            seasonsFlow: StateFlow<List<SeriesSeason>>,
        ) {
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    seasonsFlow.collect { seasons ->
                        if (seasons.isNotEmpty()) {
                            seasonTabLayout?.let { tabLayout ->
                                tabLayout.removeAllTabs()
                                seasons.forEachIndexed { index, season ->
                                    // Custom view ile tab oluştur (metin ortalanmış)
                                    val tabView =
                                        TextView(context).apply {
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
                                            setTextColor(
                                                if (index == 0) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#A0A0A0"),
                                            )
                                        }

                                    // TabLayout'un tab container'ı için layoutParams ayarla
                                    tabView.layoutParams =
                                        android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                        )

                                    val tab = tabLayout.newTab().setCustomView(tabView)
                                    tab.tag = season
                                    tabLayout.addTab(tab)
                                }

                                // İlk açılışta odağı ayarlama
                                tabLayout.post {
                                    if (tabLayout.tabCount > 0) {
                                        tabLayout.getTabAt(0)?.customView?.requestFocus()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * Seçili sezon numarasını gözlemler ve sekmeleri günceller.
         */
        fun observeSelectedSeasonNumber(
            lifecycleOwner: LifecycleOwner,
            selectedSeasonNumberFlow: StateFlow<Int?>,
        ) {
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    selectedSeasonNumberFlow.collect { selectedNumber ->
                        if (selectedNumber != null) {
                            seasonTabLayout?.let { tabLayout ->
                                // Tüm tab'ların renklerini güncelle
                                for (i in 0 until tabLayout.tabCount) {
                                    val tab = tabLayout.getTabAt(i)
                                    val isSelected = (tab?.tag as? SeriesSeason)?.seasonNumber == selectedNumber
                                    tab?.customView?.let { view ->
                                        (view as? TextView)?.setTextColor(
                                            if (isSelected) {
                                                android.graphics.Color.WHITE
                                            } else {
                                                android.graphics.Color.parseColor("#A0A0A0")
                                            },
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
        }

        /**
         * Bölümler StateFlow'sunu gözlemler ve listeyi günceller.
         */
        fun observeEpisodes(
            lifecycleOwner: LifecycleOwner,
            episodesFlow: StateFlow<List<ParsedEpisode>>,
            view: View,
            hasEpisodes: () -> Boolean,
            getEmptyStateMessage: () -> String,
        ) {
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    episodesFlow.collect { episodes ->
                        // Fragment ve view lifecycle kontrolü - güvenli UI güncellemesi için
                        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            return@collect
                        }

                        // View'ların hala bağlı olduğundan emin ol
                        if (view.parent == null) {
                            return@collect
                        }

                        try {
                            episodesAdapter?.submitList(episodes)

                            // Empty state yönetimi
                            if (episodes.isEmpty() && hasEpisodes()) {
                                episodesRecyclerView?.visibility = View.GONE
                                emptyStateContainer?.visibility = View.VISIBLE
                                emptyStateText?.text = getEmptyStateMessage()
                            } else {
                                episodesRecyclerView?.visibility = View.VISIBLE
                                emptyStateContainer?.visibility = View.GONE

                                // İzlenip geri dönülen bölüme odaklanma
                                if (lastFocusedEpisodePosition >= 0 && lastFocusedEpisodePosition < episodes.size) {
                                    episodesRecyclerView?.post {
                                        // Post içinde tekrar kontrol et
                                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && view.parent != null) {
                                            episodesRecyclerView?.let { rv ->
                                                val viewHolder =
                                                    rv.findViewHolderForAdapterPosition(
                                                        lastFocusedEpisodePosition,
                                                    )
                                                viewHolder?.itemView?.requestFocus()
                                            }
                                        }
                                    }
                                }
                            }

                            // Bölüm listesi güncellendiğinde, yani layout'un yeniden çizileceği kesinleştiğinde,
                            // bir sonraki frame'de çalışmak üzere odak isteme eylemini sıraya alıyoruz.
                            // Bu, layout pass bittikten sonra odağın güvenli bir şekilde ayarlanmasını sağlar.
                            seasonTabLayout?.post {
                                // Post içinde tekrar kontrol et
                                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && view.parent != null) {
                                    seasonTabLayout?.let { tabLayout ->
                                        val selectedTabPosition = tabLayout.selectedTabPosition
                                        if (selectedTabPosition != -1) {
                                            val selectedTab = tabLayout.getTabAt(selectedTabPosition)
                                            selectedTab?.customView?.requestFocus()
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Bölüm listesi güncellenirken hata oluştu")
                            // Hata durumunda boş state göster
                            if (view.parent != null) {
                                try {
                                    episodesRecyclerView?.visibility = View.GONE
                                    emptyStateContainer?.visibility = View.VISIBLE
                                    emptyStateText?.text = getEmptyStateMessage()
                                } catch (ex: Exception) {
                                    Timber.e(ex, "Empty state gösterilirken hata oluştu")
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * onResume'da çağrılmalı - focus yönetimi için.
         */
        fun onResume() {
            // İzlenip geri dönülen bölüme odaklanma
            episodesAdapter?.let { adapter ->
                if (lastFocusedEpisodePosition >= 0 && adapter.itemCount > lastFocusedEpisodePosition) {
                    episodesRecyclerView?.post {
                        episodesRecyclerView?.let { rv ->
                            val viewHolder = rv.findViewHolderForAdapterPosition(lastFocusedEpisodePosition)
                            viewHolder?.itemView?.requestFocus()
                        }
                    }
                }
            }
        }

        /**
         * Sezon tab layout'unu döndürür (fragment'tan erişim için).
         * Nullable döndürür, null kontrolü yapılmalıdır.
         */
        fun getSeasonTabLayout(): TabLayout? = seasonTabLayout

        /**
         * Episodes RecyclerView'ı döndürür (fragment'tan erişim için).
         * Nullable döndürür, null kontrolü yapılmalıdır.
         */
        fun getEpisodesRecyclerView(): RecyclerView? = episodesRecyclerView

        /**
         * Episodes adapter'ı döndürür (fragment'tan erişim için).
         * Nullable döndürür, null kontrolü yapılmalıdır.
         */
        fun getEpisodesAdapter(): EpisodesAdapter? = episodesAdapter
    }
