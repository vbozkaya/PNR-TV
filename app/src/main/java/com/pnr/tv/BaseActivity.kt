package com.pnr.tv

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pnr.tv.util.LocaleHelper

/**
 * Projedeki tüm Activity'ler için ortak davranışları ve ayarları içeren temel sınıftır.
 * Bu sınıfın temel amacı, tüm ekranlarda tutarlı bir görünüm ve davranış sağlamaktır.
 *
 * Sorumlulukları:
 * - **Tam Ekran Ayarı:** Uygulamanın TV ekranını kaplamasını sağlar.
 * - **Ekran Bilgileri:** Cihazın ekran boyutu gibi bilgilere kolay erişim için yardımcı metodlar sunar.
 * - **Dil Yönetimi:** LocaleHelper ile dil değiştirme desteği
 *
 * Yeni bir Activity oluşturulduğunda, `AppCompatActivity` yerine bu sınıftan türetilmelidir.
 */
abstract class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()
    }

    override fun onStart() {
        super.onStart()
        // MainActivity hariç tüm activity'lerde navbar'ı setup et
        if (this !is MainActivity) {
            setupNavbar()
        }
    }

    /**
     * Navbar'ı setup eder ve home butonuna click listener ekler.
     * Her activity'de navbar title'ı ayarlamak için [setNavbarTitle] metodunu override edebilirsiniz.
     */
    protected fun setupNavbar() {
        // Geri butonuna click listener ekle
        val backButton = findViewById<View>(R.id.btn_navbar_back)
        backButton?.setOnClickListener {
            finish()
        }

        // Home butonuna click listener ekle
        val homeButton = findViewById<View>(R.id.btn_navbar_home)
        homeButton?.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }

        // Navbar title'ı ayarla
        val titleText = getNavbarTitle()
        if (titleText != null) {
            val titleTextView = findViewById<TextView>(R.id.txt_navbar_title)
            titleTextView?.text = titleText
        }

        // Filter butonunu ve arama çubuğunu gizle (BaseActivity'lerde gösterilmez, sadece filmler ve diziler sayfalarında gösterilir)
        val filterButton = findViewById<View>(R.id.btn_navbar_filter)
        filterButton?.visibility = View.GONE
        val searchEditText = findViewById<android.widget.EditText>(R.id.edt_navbar_search)
        searchEditText?.visibility = View.GONE
    }

    /**
     * Navbar'da gösterilecek başlığı döndürür.
     * Override edilerek her activity'de farklı başlık ayarlanabilir.
     * @return Navbar başlığı string resource ID veya null (başlık gösterilmezse)
     */
    protected open fun getNavbarTitle(): String? {
        return null
    }

    /**
     * Aktivite oluşturulduğunda çağrılır ve TV için tam ekran kullanıcı arayüzü ayarlarını yapar.
     * Bu metod, sistem çubuklarını (navigation bar, status bar) gizleyerek içeriğin ekranın
     * tamamını kullanmasını sağlar.
     */
    private fun setupWindow() {
        // ActionBar'ı gizle
        supportActionBar?.hide()

        // TV için tam ekran ayarları. Android 11 (R) ve sonrası için `setDecorFitsSystemWindows(false)`
        // kullanılırken, eski sürümler için `systemUiVisibility` flag'leri kullanılır.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.hide(android.view.WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
        }

        // Tam ekran modu
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Ekran yapılandırması değiştiğinde (örn: dil, çözünürlük) yapılacak işlemler buraya eklenebilir.
        // Şu an için Android'in varsayılan davranışı yeterlidir.
    }

    /**
     * Cihaz ekranının genişliğini DP (Density-independent Pixel) cinsinden döndürür.
     * @return Ekran genişliği (dp).
     */
    protected fun getScreenWidthDp(): Int {
        val metrics = resources.displayMetrics
        return (metrics.widthPixels / metrics.density).toInt()
    }

    /**
     * Cihaz ekranının yüksekliğini DP (Density-independent Pixel) cinsinden döndürür.
     * @return Ekran yüksekliği (dp).
     */
    protected fun getScreenHeightDp(): Int {
        val metrics = resources.displayMetrics
        return (metrics.heightPixels / metrics.density).toInt()
    }
}
