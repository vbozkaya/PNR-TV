package com.pnr.tv.ui.player.panel

import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pnr.tv.databinding.PlayerChannelListPanelBinding
import com.pnr.tv.repository.ContentRepository
import com.pnr.tv.ui.player.adapter.ChannelListAdapter
import com.pnr.tv.ui.player.handler.ChannelListListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Kanal listesi paneli yönetimi için coordinator sınıfı.
 * PlayerActivity'den kanal listesi mantığını ayırmak için oluşturulmuştur.
 * Görünürlük ve focus yönetimi ayrı handler sınıflarına taşınmıştır.
 */
class ChannelListPanel(
    private val binding: PlayerChannelListPanelBinding,
    private val lifecycleScope: CoroutineScope,
    private val contentRepository: ContentRepository,
    private val listener: ChannelListListener,
) {
    private var channelListAdapter: ChannelListAdapter? = null
    private var channelListLayoutManager: LinearLayoutManager? = null

    // Kanal listesi verileri
    private var categoryId: Int? = null
    private var currentChannelId: Int? = null
    private var previousCategoryId: Int? = null // Kategori değişikliğini tespit etmek için

    // Handler'lar
    private val visibilityHandler: ChannelPanelVisibilityHandler
    private val focusHandler: ChannelPanelFocusHandler

    init {
        // Visibility handler'ı oluştur
        visibilityHandler = ChannelPanelVisibilityHandler(
            binding = binding,
            lifecycleScope = lifecycleScope,
        )

        // Focus handler'ı oluştur
        focusHandler = ChannelPanelFocusHandler(
            binding = binding,
            visibilityHandler = visibilityHandler,
            onHideRequested = ::hide,
        )

        setupChannelListPanel()
    }

    /**
     * Panel'i setup eder.
     */
    private fun setupChannelListPanel() {
        // RecyclerView'ı focusable yapma (sadece içindeki item'lar focusable olacak)
        binding.recyclerChannelList.isFocusable = false

        // Kanal listesi adapter'ı
        channelListAdapter =
            ChannelListAdapter(
                channels = emptyList(),
                selectedChannelId = null,
                onChannelSelected = { channel ->
                    listener.onChannelSelected(channel)
                },
            )
        channelListLayoutManager = LinearLayoutManager(binding.root.context)
        binding.recyclerChannelList.layoutManager = channelListLayoutManager
        binding.recyclerChannelList.adapter = channelListAdapter

        // Kanal listesi RecyclerView için focus scroll
        focusHandler.setupRecyclerViewFocusScroll(binding.recyclerChannelList, channelListLayoutManager!!)

        // Panel başlangıçta gizli
        binding.playerChannelListPanel.visibility = View.GONE
    }

    /**
     * Panel'i gösterir.
     * @param categoryId Kategori ID (kanalları yüklemek için)
     * @param currentChannelId Mevcut kanal ID (seçili kanalı göstermek için)
     */
    fun show(
        categoryId: Int?,
        currentChannelId: Int?,
    ) {
        // Sadece canlı yayınlarda göster
        if (categoryId == null || currentChannelId == null) {
            return
        }

        // Kategori değişikliğini tespit et
        val categoryChanged = previousCategoryId != null && previousCategoryId != categoryId

        this.categoryId = categoryId
        this.currentChannelId = currentChannelId
        this.previousCategoryId = categoryId // Bir sonraki çağrı için kaydet

        // ÖNCE kanalları yükle, SONRA paneli göster
        lifecycleScope.launch {
            try {
                // Kanalları yükle
                val channels = contentRepository.getLiveStreamsByCategoryIdSync(categoryId)

                // Kanallar boşsa işlem yapma
                if (channels.isEmpty()) {
                    return@launch
                }

                // UI thread'de adapter'ı güncelle ve paneli göster
                withContext(Dispatchers.Main) {
                    updateAdapterAndRecyclerView(channels, currentChannelId)

                    // Visibility handler ile paneli göster
                    visibilityHandler.show(categoryChanged) {
                        // Animasyon bittikten sonra focus yönetimi
                        if (!categoryChanged) {
                            restoreFocusToCurrentChannel(currentChannelId)
                        }
                    }
                }
            } catch (e: Exception) {
                // Hata olsa bile paneli göster
                withContext(Dispatchers.Main) {
                    binding.playerChannelListPanel.visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * Panel'i gizler.
     */
    fun hide() {
        visibilityHandler.hide()
    }

    /**
     * Panel'in görünür olup olmadığını döndürür.
     */
    fun isVisible(): Boolean {
        return visibilityHandler.isVisible()
    }

    /**
     * Panel'in animasyon halinde olup olmadığını döndürür.
     */
    fun isAnimating(): Boolean {
        return visibilityHandler.isAnimating()
    }

    /**
     * Panel açıkken tuş olaylarını handle eder.
     * @param keyCode Tuş kodu
     * @param event Tuş olayı
     * @return Olay işlendi ise true, aksi halde false
     */
    fun handleKeyEvent(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        return focusHandler.handleKeyEvent(keyCode, event)
    }

    /**
     * Mevcut kanal ID'sini günceller.
     */
    fun updateCurrentChannelId(channelId: Int?) {
        this.currentChannelId = channelId
        // Adapter'ı güncelle
        channelListAdapter?.let { adapter ->
            val channels = adapter.channelsList
            adapter.updateChannels(channels, channelId)
        }
    }

    /**
     * Adapter ve RecyclerView'ı günceller.
     */
    private fun updateAdapterAndRecyclerView(
        channels: List<com.pnr.tv.db.entity.LiveStreamEntity>,
        selectedChannelId: Int?,
    ) {
        // Adapter'ı güncelle (yeniden oluşturma, mevcut adapter'ı kullan)
        channelListAdapter?.updateChannels(channels, selectedChannelId)

        // Adapter yoksa oluştur
        if (channelListAdapter == null) {
            channelListAdapter =
                ChannelListAdapter(
                    channels = channels,
                    selectedChannelId = selectedChannelId,
                    onChannelSelected = { channel ->
                        listener.onChannelSelected(channel)
                    },
                )
            binding.recyclerChannelList.adapter = channelListAdapter
        }

        // Layout manager yoksa oluştur
        if (channelListLayoutManager == null) {
            channelListLayoutManager = LinearLayoutManager(binding.root.context)
            binding.recyclerChannelList.layoutManager = channelListLayoutManager
            focusHandler.setupRecyclerViewFocusScroll(binding.recyclerChannelList, channelListLayoutManager!!)
        }

        // RecyclerView ayarları
        binding.recyclerChannelList.setHasFixedSize(false)
        binding.recyclerChannelList.isNestedScrollingEnabled = true
        binding.recyclerChannelList.itemAnimator = null

        // RecyclerView'ı görünür yap
        binding.recyclerChannelList.visibility = View.VISIBLE
    }

    /**
     * Mevcut kanala focus'u restore eder.
     */
    private fun restoreFocusToCurrentChannel(channelId: Int?) {
        val currentChannelPosition =
            channelListAdapter?.let { adapter ->
                adapter.channelsList.indexOfFirst { it.streamId == channelId }
            } ?: -1

        if (currentChannelPosition >= 0) {
            binding.recyclerChannelList.post {
                val currentChannelViewHolder =
                    binding.recyclerChannelList.findViewHolderForAdapterPosition(
                        currentChannelPosition,
                    )
                currentChannelViewHolder?.itemView?.requestFocus()
            }
        }
    }

}
