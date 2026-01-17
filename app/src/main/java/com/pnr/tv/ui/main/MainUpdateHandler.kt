package com.pnr.tv.ui.main

import android.content.Context
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.pnr.tv.ui.main.MainFragment
import com.pnr.tv.R
import com.pnr.tv.core.constants.UIConstants
import com.pnr.tv.databinding.ActivityMainBinding
import com.pnr.tv.extensions.hide
import com.pnr.tv.extensions.show
import com.pnr.tv.ui.shared.SharedViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * MainActivity için güncelleme durumu ve UI yönetimi handler sınıfı.
 * Update state observation, user interaction management ve focus yönetimini merkezi bir şekilde yönetir.
 * Android TV için kritik olan focus mantığını korur.
 */
class MainUpdateHandler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private lateinit var binding: ActivityMainBinding
        private lateinit var viewModel: SharedViewModel
        private lateinit var lifecycleScope: CoroutineScope
        private lateinit var fragmentManager: FragmentManager

        /**
         * Handler'ı başlatır ve gözlemlemeleri başlatır.
         * MainActivity'nin onCreate'inde çağrılmalıdır.
         */
        fun setup(
            binding: ActivityMainBinding,
            viewModel: SharedViewModel,
            lifecycleScope: CoroutineScope,
            fragmentManager: FragmentManager,
        ) {
            this.binding = binding
            this.viewModel = viewModel
            this.lifecycleScope = lifecycleScope
            this.fragmentManager = fragmentManager

            // Gözlemlemeleri başlat
            observeUpdateState()
            observeAddedContentCounts()
        }

        /**
         * Güncelleme durumunu gözlemler ve UI'ı buna göre günceller.
         */
        private fun observeUpdateState() {
            lifecycleScope.launch {
                viewModel.updateState.collectLatest { state ->
                    when (state) {
                        SharedViewModel.UpdateState.LOADING -> {
                            // Yüklenme başladı - overlay'i göster ve mesajı ayarla
                            binding.loadingOverlay.show()
                            binding.txtLoadingMessage.text = context.getString(R.string.loading_content)
                            binding.txtAddedContent.visibility = android.view.View.GONE
                            binding.txtAddedContent.text = ""
                            // Retry butonunu gizle (loading durumunda gerekli değil)
                            binding.btnRetryError.visibility = android.view.View.GONE
                            // Kullanıcı etkileşimini engelle
                            disableUserInteraction()
                        }
                        SharedViewModel.UpdateState.COMPLETED -> {
                            // Güncelleme tamamlandı - overlay hala görünür, mesajı değiştir
                            binding.loadingOverlay.show()
                            binding.txtLoadingMessage.text = context.getString(R.string.loading_completed)
                            // Retry butonunu gizle (başarılı durumda gerekli değil)
                            binding.btnRetryError.visibility = android.view.View.GONE
                            // Kullanıcı etkileşimini engelle (overlay hala görünür)
                            disableUserInteraction()

                            // İçerik sayılarını göster (0 olsa bile göster)
                            // StateFlow güncellemesi için kısa bir gecikme ekle
                            lifecycleScope.launch {
                                delay(300) // StateFlow güncellemesi için biraz bekle
                                val counts = viewModel.addedContentCounts.value
                                if (counts != null) {
                                    // 0 olsa bile göster
                                    val contentText =
                                        buildString {
                                            append(context.getString(R.string.added_content_title))
                                            append("\n")
                                            append(context.getString(R.string.added_content_movies, counts.moviesCount))
                                            append("\n")
                                            append(context.getString(R.string.added_content_series, counts.seriesCount))
                                        }
                                    binding.txtAddedContent.text = contentText
                                    binding.txtAddedContent.visibility = android.view.View.VISIBLE
                                    Timber.d(
                                        "✅ COMPLETED handler'da içerik sayıları gösterildi: Film=${counts.moviesCount}, Dizi=${counts.seriesCount}",
                                    )
                                } else {
                                    // Sayılar henüz güncellenmemiş, observeAddedContentCounts ile gösterilecek
                                    Timber.d("⚠️ AddedContentCounts henüz güncellenmemiş, observeAddedContentCounts bekleniyor")
                                }
                            }

                            // Belirli bir süre sonra durumu IDLE'a çek (UI mantığı)
                            lifecycleScope.launch {
                                delay(UIConstants.DelayDurations.UPDATE_COMPLETED_DELAY)
                                viewModel.resetUpdateState()
                            }
                        }
                        SharedViewModel.UpdateState.ERROR -> {
                            // Hata durumu - overlay'i göster ve hata mesajını göster
                            binding.loadingOverlay.show()
                            val errorMsg = viewModel.errorMessage.value
                            // Hata mesajını direkt göster (error_with_message formatı yerine)
                            binding.txtLoadingMessage.text = errorMsg ?: context.getString(R.string.error_unknown)

                            // Retry butonunu göster ve focus ver
                            binding.btnRetryError.visibility = android.view.View.VISIBLE
                            binding.btnRetryError.requestFocus()

                            // Kullanıcı etkileşimini engelle (retry butonu overlay içinde olduğu için çalışmaya devam eder)
                            disableUserInteraction()

                            // 3 saniye sonra otomatik olarak overlay'i kapat
                            lifecycleScope.launch {
                                delay(UIConstants.DelayDurations.UPDATE_ERROR_DELAY)
                                viewModel.resetUpdateState()
                            }
                        }
                        SharedViewModel.UpdateState.IDLE -> {
                            // Durum sıfırlandı - overlay'i gizle
                            binding.loadingOverlay.hide()
                            binding.txtAddedContent.visibility = android.view.View.GONE
                            binding.txtAddedContent.text = ""
                            // Kullanıcı etkileşimini tekrar etkinleştir
                            enableUserInteraction()
                            // Focus yönetimi - overlay gizlendikten sonra uygun yere focus ver
                            restoreFocusAfterUpdate()
                        }
                    }
                }
            }
        }

        /**
         * Eklenen içerik sayılarını gözlemler ve UI'ı günceller.
         */
        private fun observeAddedContentCounts() {
            lifecycleScope.launch {
                // Hem updateState hem de addedContentCounts değişikliklerini dinle
                combine(
                    viewModel.updateState,
                    viewModel.addedContentCounts,
                ) { state, counts ->
                    Pair(state, counts)
                }.collectLatest { (state, counts) ->
                    Timber.d("🔔 observeAddedContentCounts: state=$state, counts=$counts")
                    if (state == SharedViewModel.UpdateState.COMPLETED && counts != null) {
                        // İçerik sayılarını göster (0 olsa bile göster - kullanıcı bilgilendirilsin)
                        val contentText =
                            buildString {
                                append(context.getString(R.string.added_content_title))
                                append("\n")
                                append(context.getString(R.string.added_content_movies, counts.moviesCount))
                                append("\n")
                                append(context.getString(R.string.added_content_series, counts.seriesCount))
                            }
                        binding.txtAddedContent.text = contentText
                        binding.txtAddedContent.visibility = android.view.View.VISIBLE
                        Timber.d(
                            "✅ observeAddedContentCounts'da içerik sayıları gösterildi: Film=${counts.moviesCount}, Dizi=${counts.seriesCount}",
                        )
                    } else {
                        if (state != SharedViewModel.UpdateState.COMPLETED) {
                            binding.txtAddedContent.visibility = android.view.View.GONE
                            binding.txtAddedContent.text = ""
                        }
                        // COMPLETED durumunda ama counts null ise, COMPLETED handler'da gösterilecek
                    }
                }
            }
        }

        /**
         * Overlay gösterilirken kullanıcı etkileşimini engeller.
         * Fragment container ve üst menü butonlarını devre dışı bırakır.
         */
        private fun disableUserInteraction() {
            // Fragment container'ı devre dışı bırak
            binding.fragmentContainer.isEnabled = false
            binding.fragmentContainer.isClickable = false
            binding.fragmentContainer.isFocusable = false
            binding.fragmentContainer.isFocusableInTouchMode = false

            // Üst menü butonlarını devre dışı bırak (retry butonu hariç - o overlay içinde)
            binding.btnUpdate.isEnabled = false
            binding.btnUpdate.isClickable = false
            binding.btnUpdate.isFocusable = false
            binding.btnUpdate.isFocusableInTouchMode = false

            binding.btnUsers.isEnabled = false
            binding.btnUsers.isClickable = false
            binding.btnUsers.isFocusable = false
            binding.btnUsers.isFocusableInTouchMode = false

            binding.btnSettings.isEnabled = false
            binding.btnSettings.isClickable = false
            binding.btnSettings.isFocusable = false
            binding.btnSettings.isFocusableInTouchMode = false

            binding.btnExit.isEnabled = false
            binding.btnExit.isClickable = false
            binding.btnExit.isFocusable = false
            binding.btnExit.isFocusableInTouchMode = false

            // ODAK BLOKLAMA: Fragment içine focus girmesini engelle
            binding.fragmentContainer.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS

            // Overlay'e focus ver (tüm etkileşimleri yakalamak için)
            binding.loadingOverlay.requestFocus()
        }

        /**
         * Overlay gizlendikten sonra kullanıcı etkileşimini tekrar etkinleştirir.
         * Fragment container ve üst menü butonlarını tekrar aktif eder.
         */
        private fun enableUserInteraction() {
            // Fragment container'ı tekrar etkinleştir
            binding.fragmentContainer.isEnabled = true
            binding.fragmentContainer.isClickable = true
            binding.fragmentContainer.isFocusable = true
            binding.fragmentContainer.isFocusableInTouchMode = true

            // Üst menü butonlarını tekrar etkinleştir
            binding.btnUpdate.isEnabled = true
            binding.btnUpdate.isClickable = true
            binding.btnUpdate.isFocusable = true
            binding.btnUpdate.isFocusableInTouchMode = true

            binding.btnUsers.isEnabled = true
            binding.btnUsers.isClickable = true
            binding.btnUsers.isFocusable = true
            binding.btnUsers.isFocusableInTouchMode = true

            binding.btnSettings.isEnabled = true
            binding.btnSettings.isClickable = true
            binding.btnSettings.isFocusable = true
            binding.btnSettings.isFocusableInTouchMode = true

            binding.btnExit.isEnabled = true
            binding.btnExit.isClickable = true
            binding.btnExit.isFocusable = true
            binding.btnExit.isFocusableInTouchMode = true

            // ODAK GERİ YÜKLEME: Fragment içine focus girmesine izin ver
            binding.fragmentContainer.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS

            // Overlay'in focus'unu kaldır
            binding.loadingOverlay.clearFocus()
            binding.loadingOverlay.isFocusable = false
            binding.loadingOverlay.isFocusableInTouchMode = false
        }

        /**
         * Güncelleme tamamlandıktan sonra uygun yere focus verir.
         * MainFragment görünürse update butonuna, diğer fragment'larda ise
         * o fragment'ın uygun yerine focus verir.
         * Android TV için kritik olan focus mantığını korur.
         */
        private fun restoreFocusAfterUpdate() {
            binding.root.post {
                val currentFragment = fragmentManager.findFragmentById(R.id.fragment_container)

                when {
                    // Ana sayfada ise update butonuna focus ver
                    currentFragment is MainFragment || currentFragment == null -> {
                        binding.btnUpdate.requestFocus()
                        // Birden fazla kez deneyelim (Android'in otomatik odak yönetimi ile yarışmak için)
                        binding.root.postDelayed({
                            binding.btnUpdate.requestFocus()
                        }, 50)
                        binding.root.postDelayed({
                            binding.btnUpdate.requestFocus()
                        }, 150)
                    }
                    // Diğer fragment'larda ise fragment'ın kendi focus yönetimine bırak
                    else -> {
                        // Fragment'ın view'ı varsa ve focusable bir child'ı varsa ona focus ver
                        currentFragment.view?.let { fragmentView ->
                            // Önce fragment view'ına focus ver, fragment kendi içinde yönetir
                            fragmentView.requestFocus()
                            // Eğer fragment'ın focusable bir child'ı varsa ona geç
                            fragmentView.postDelayed({
                                val focusableChild = fragmentView.findFocus()
                                if (focusableChild == null) {
                                    // Focusable child yoksa, fragment view'ına focus ver
                                    fragmentView.requestFocus()
                                }
                            }, 100)
                        }
                    }
                }
            }
        }

        /**
         * Veri yoksa gösterilecek uyarı mesajı.
         * Fragment'lar tarafından çağrılır.
         * @param message Gösterilecek mesaj (varsayılan: data_required_warning)
         */
        fun showDataRequiredWarning(message: String? = null) {
            binding.loadingOverlay.show()
            binding.txtLoadingMessage.text = message ?: context.getString(R.string.data_required_warning)
            binding.txtAddedContent.visibility = android.view.View.GONE
            binding.txtAddedContent.text = ""
            binding.btnRetryError.visibility = android.view.View.GONE
            disableUserInteraction()
        }

        /**
         * Veri yoksa gösterilen uyarı mesajını gizler.
         */
        fun hideDataRequiredWarning() {
            binding.loadingOverlay.hide()
            enableUserInteraction()
        }

        /**
         * Kullanıcı yoksa gösterilecek uyarı mesajı.
         * Fragment'lar tarafından çağrılır.
         */
        fun showUserRequiredWarning() {
            binding.loadingOverlay.show()
            binding.txtLoadingMessage.text = context.getString(R.string.user_required_warning)
            binding.txtAddedContent.visibility = android.view.View.GONE
            binding.txtAddedContent.text = ""
            binding.btnRetryError.visibility = android.view.View.GONE
            disableUserInteraction()
        }

        /**
         * Kullanıcı yoksa gösterilen uyarı mesajını gizler.
         */
        fun hideUserRequiredWarning() {
            binding.loadingOverlay.hide()
            enableUserInteraction()
        }
    }
