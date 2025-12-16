package com.pnr.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import com.pnr.tv.databinding.ActivityMainBinding
import com.pnr.tv.extensions.hide
import com.pnr.tv.extensions.show
import com.pnr.tv.ui.base.ToolbarController
import com.pnr.tv.util.BackgroundManager
import com.pnr.tv.util.ViewerInitializer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity(), ToolbarController {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var viewerInitializer: ViewerInitializer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeCurrentUser()
        observeUpdateState()

        binding.btnUpdate.setOnClickListener {
            viewModel.refreshAllContent()
        }

        // Retry butonu için click listener
        binding.btnRetryError.setOnClickListener {
            viewModel.refreshAllContent()
        }

        binding.btnUsers.setOnClickListener {
            startActivity(Intent(this, UserManagementActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnExit.setOnClickListener {
            Timber.d("Exit button clicked")
            showExitDialog()
        }

        // Set initial focus
        binding.btnUpdate.requestFocus()

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, MainFragment())
            }
        }

        // Initialize default viewer if needed
        lifecycleScope.launch {
            viewerInitializer.initializeIfNeeded()
        }

        // Geri tuşu davranışını yönet
        setupBackPressedHandler()

        // MainActivity için özel arkaplan yükleme (binding.root'a ekle)
        loadMainActivityBackground()
    }

    /**
     * MainActivity için özel arkaplan yükleme.
     * DecorView yerine binding.root'a (activity_main.xml'deki root FrameLayout) arkaplan ekler.
     * Bu sayede fragment_container'ın arkasında görünür.
     */
    private fun loadMainActivityBackground() {
        timber.log.Timber.tag("BACKGROUND").d("🎬 MainActivity.loadMainActivityBackground() çağrıldı")
        lifecycleScope.launch {
            timber.log.Timber.tag(
                "BACKGROUND",
            ).d(
                "📐 binding.root - View: ${binding.root.javaClass.simpleName}, Width: ${binding.root.width}, Height: ${binding.root.height}",
            )

            // Önce cache'den kontrol et (hızlı)
            val cached = BackgroundManager.getCachedBackground()
            if (cached != null) {
                timber.log.Timber.tag("BACKGROUND").d("✅ Cache'den arkaplan uygulanıyor (MainActivity binding.root)")
                binding.root.background = cached
                timber.log.Timber.tag(
                    "BACKGROUND",
                ).d("✅ Arkaplan uygulandı - binding.root background: ${binding.root.background?.javaClass?.simpleName}")
                return@launch
            }

            timber.log.Timber.tag("BACKGROUND").d("⏳ Cache'de yok, yükleme başlatılıyor... (MainActivity)")

            // Cache'de yoksa yükle
            BackgroundManager.loadBackground(
                context = this@MainActivity,
                imageLoader = imageLoader,
                onSuccess = { drawable ->
                    timber.log.Timber.tag(
                        "BACKGROUND",
                    ).d("✅ onSuccess callback çağrıldı - Drawable: ${drawable.javaClass.simpleName} (MainActivity)")
                    binding.root.background = drawable
                    timber.log.Timber.tag(
                        "BACKGROUND",
                    ).d("✅ Arkaplan uygulandı - binding.root background: ${binding.root.background?.javaClass?.simpleName}")
                },
                onError = {
                    timber.log.Timber.tag("BACKGROUND").w("⚠️ onError callback çağrıldı, fallback deneniyor... (MainActivity)")
                    // Hata durumunda fallback kullan (theme'de zaten tanımlı)
                    val fallback = BackgroundManager.getFallbackBackground(this@MainActivity)
                    if (fallback != null) {
                        binding.root.background = fallback
                        timber.log.Timber.tag("BACKGROUND").d("✅ Fallback arkaplan uygulandı (MainActivity)")
                    } else {
                        timber.log.Timber.tag("BACKGROUND").e("❌ Fallback arkaplan da null! (MainActivity)")
                    }
                },
            )
        }
    }

    /**
     * Geri tuşu davranışını ayarlar.
     * Ana sayfadayken (MainFragment görünürken ve backstack boşken) geri tuşuna basıldığında
     * çıkış dialogu gösterilir. Diğer durumlarda normal fragment geri navigasyonu yapılır.
     */
    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Backstack'te fragment var mı kontrol et
                    val fragmentCount = supportFragmentManager.backStackEntryCount
                    val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

                    // Eğer backstack'te fragment varsa, normal fragment geri navigasyonu yap
                    if (fragmentCount > 0) {
                        supportFragmentManager.popBackStack()
                        return
                    }

                    // Ana sayfadayken (MainFragment görünürken ve backstack boşken) dialog göster
                    if (currentFragment is MainFragment || currentFragment == null) {
                        Timber.d("Back button pressed on main screen - showing exit dialog")
                        showExitDialog()
                    }
                    // Eğer backstack boşsa ve MainFragment değilse, hiçbir şey yapma
                    // (Bu durum normalde olmamalı ama güvenlik için bırakıldı)
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()

        // Set initial focus to the update button every time the main screen is visible
        // Activity geçişlerinden sonra focus kaybını önlemek için daha agresif focus yönetimi
        binding.root.post {
            // Check if MainFragment is currently visible
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment is MainFragment || currentFragment == null) {
                // Önce container'ları focusable yapmayı engelle (MainFragment bunu yapacak)
                // Sadece update button'a focus ver
                binding.btnUpdate.requestFocus()

                // Birden fazla kez deneyelim (Android'in otomatik odak yönetimi ile yarışmak için)
                binding.root.postDelayed({
                    binding.btnUpdate.requestFocus()
                }, 50)

                binding.root.postDelayed({
                    binding.btnUpdate.requestFocus()
                }, 150)

                // Activity geçişlerinden sonra daha uzun bir süre sonra da focus ver
                binding.root.postDelayed({
                    binding.btnUpdate.requestFocus()
                }, 300)

                // Son bir kontrol daha (container'lar focusable olmadan önce)
                binding.root.postDelayed({
                    // Eğer focus hala update button'da değilse tekrar ver
                    val currentFocus = window.currentFocus
                    if (currentFocus?.id != binding.btnUpdate.id) {
                        binding.btnUpdate.requestFocus()
                    }
                }, 350)
            }
        }
    }

    private fun observeCurrentUser() {
        viewModel.currentUser.observe(this) { user ->
            if (user != null) {
                // Kullanıcı adını hemen göster
                binding.tvCurrentUser.text = getString(R.string.current_user_label, user.accountName)
            } else {
                binding.tvCurrentUser.text = getString(R.string.current_user_none)
            }
        }
    }

    private fun observeUpdateState() {
        lifecycleScope.launch {
            viewModel.updateState.collectLatest { state ->
                when (state) {
                    MainViewModel.UpdateState.LOADING -> {
                        // Yüklenme başladı - overlay'i göster ve mesajı ayarla
                        binding.loadingOverlay.show()
                        binding.txtLoadingMessage.text = getString(R.string.loading_content)
                        // Retry butonunu gizle (loading durumunda gerekli değil)
                        binding.btnRetryError.visibility = android.view.View.GONE
                    }
                    MainViewModel.UpdateState.COMPLETED -> {
                        // Güncelleme tamamlandı - overlay hala görünür, mesajı değiştir
                        binding.loadingOverlay.show()
                        binding.txtLoadingMessage.text = getString(R.string.loading_completed)
                        // Retry butonunu gizle (başarılı durumda gerekli değil)
                        binding.btnRetryError.visibility = android.view.View.GONE

                        // Belirli bir süre sonra durumu IDLE'a çek (UI mantığı)
                        lifecycleScope.launch {
                            delay(Constants.DelayDurations.UPDATE_COMPLETED_DELAY)
                            viewModel.resetUpdateState()
                        }
                    }
                    MainViewModel.UpdateState.ERROR -> {
                        // Hata durumu - overlay'i göster ve hata mesajını göster
                        binding.loadingOverlay.show()
                        val errorMsg = viewModel.errorMessage.value
                        // Hata mesajını direkt göster (error_with_message formatı yerine)
                        binding.txtLoadingMessage.text = errorMsg ?: getString(R.string.error_unknown)

                        // Retry butonunu göster ve focus ver
                        binding.btnRetryError.visibility = android.view.View.VISIBLE
                        binding.btnRetryError.requestFocus()

                        // Otomatik kapanmayı kaldırdık - kullanıcı retry yapabilir veya overlay'i kapatabilir
                    }
                    MainViewModel.UpdateState.IDLE -> {
                        // Durum sıfırlandı - overlay'i gizle
                        binding.loadingOverlay.hide()
                    }
                }
            }
        }
    }

    override fun showTopMenu() {
        binding.layoutCurrentUser.show()
        binding.btnExit.show()
        binding.btnSettings.show()
        binding.btnUsers.show()
        binding.btnUpdate.show()
    }

    override fun hideTopMenu() {
        binding.layoutCurrentUser.hide()
        binding.btnExit.hide()
        binding.btnSettings.hide()
        binding.btnUsers.hide()
        binding.btnUpdate.hide()
    }

    /**
     * Üst menü butonlarından birine focus verilmiş mi kontrol eder
     */
    fun isTopMenuButtonFocused(): Boolean {
        val focusedView = window?.currentFocus ?: return false
        return focusedView.id == binding.btnUpdate.id ||
            focusedView.id == binding.btnUsers.id ||
            focusedView.id == binding.btnSettings.id ||
            focusedView.id == binding.btnExit.id
    }

    /**
     * Güncelle butonuna odak verir. Fragment'lar tarafından çağrılabilir.
     */
    fun requestFocusOnUpdateButton() {
        binding.btnUpdate.requestFocus()
    }

    private fun showExitDialog() {
        try {
            Timber.d("showExitDialog called")
            val dialog =
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_exit_title))
                    .setMessage(getString(R.string.dialog_exit_message))
                    .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                        Timber.d("User confirmed exit")
                        finishAffinity()
                    }
                    .setNegativeButton(getString(R.string.dialog_no)) { dialog, _ ->
                        Timber.d("User cancelled exit")
                        dialog.dismiss()
                    }
                    .setCancelable(false)
                    .create()

            dialog.show()
            // Güvenlik için "Hayır" butonuna focus ver
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
        } catch (e: Exception) {
            Timber.e(e, "Error showing dialog")
        }
    }
}
