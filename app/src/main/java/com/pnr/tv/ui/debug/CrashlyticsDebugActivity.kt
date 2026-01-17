package com.pnr.tv.ui.debug

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pnr.tv.R
import com.pnr.tv.util.CrashlyticsHelper
import com.pnr.tv.util.LifecycleTracker
import timber.log.Timber

/**
 * Firebase Crashlytics debug ve test paneli.
 *
 * Bu aktivite sadece debug build'lerde görünür ve şunları sağlar:
 * - Son crash raporlarını görüntüleme
 * - Test crash gönderme
 * - Custom key'leri görüntüleme
 * - Log mesajları gönderme
 *
 * NOT: Production build'lerde bu aktivite görünmez.
 */
class CrashlyticsDebugActivity : AppCompatActivity() {
    private lateinit var crashlytics: FirebaseCrashlytics
    private lateinit var infoTextView: TextView
    private lateinit var logsTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sadece debug build'lerde çalış
        val isDebug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!isDebug) {
            finish()
            return
        }

        setContentView(R.layout.activity_crashlytics_debug)

        crashlytics = FirebaseCrashlytics.getInstance()

        infoTextView = findViewById(R.id.tv_crashlytics_info)
        logsTextView = findViewById(R.id.tv_crashlytics_logs)

        setupButtons()
        displayInfo()
    }

    private fun setupButtons() {
        // Test crash gönder
        findViewById<View>(R.id.btn_test_crash)?.setOnClickListener {
            testCrash()
        }

        // Test non-fatal exception gönder
        findViewById<View>(R.id.btn_test_exception)?.setOnClickListener {
            testNonFatalException()
        }

        // Test log gönder
        findViewById<View>(R.id.btn_test_log)?.setOnClickListener {
            testLog()
        }

        // Custom key ekle
        findViewById<View>(R.id.btn_add_custom_key)?.setOnClickListener {
            addCustomKey()
        }

        // Kullanıcı akışı bilgisi ekle
        findViewById<View>(R.id.btn_set_user_flow)?.setOnClickListener {
            setUserFlow()
        }

        // Bilgileri yenile
        findViewById<View>(R.id.btn_refresh)?.setOnClickListener {
            displayInfo()
        }

        // Log'ları temizle
        findViewById<View>(R.id.btn_clear_logs)?.setOnClickListener {
            clearLogs()
        }

        // Lifecycle loglarını gönder
        findViewById<View>(R.id.btn_send_lifecycle_logs)?.setOnClickListener {
            sendLifecycleLogs()
        }
    }

    private fun displayInfo() {
        val info =
            buildString {
                append("=== Firebase Crashlytics Debug Info ===\n\n")

                // App bilgileri
                append("App Version: ${packageManager.getPackageInfo(packageName, 0).versionName}\n")
                append("Version Code: ${packageManager.getPackageInfo(packageName, 0).versionCode}\n")
                append(
                    "Build Type: ${if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) "Debug" else "Release"}\n\n",
                )

                // Cihaz bilgileri
                append("Device Model: ${android.os.Build.MODEL}\n")
                append("Android Version: ${android.os.Build.VERSION.SDK_INT}\n")
                append("Manufacturer: ${android.os.Build.MANUFACTURER}\n\n")

                // Crashlytics durumu
                append("Crashlytics Status: Active\n")
                append("Note: Crash reports are sent to Firebase Console\n")
                append("View reports at: https://console.firebase.google.com\n\n")

                append("=== Custom Keys ===\n")
                append("Custom keys are set in PnrTvApplication.kt\n")
                append("These keys appear in every crash report.\n\n")

                append("=== Usage ===\n")
                append("1. Use CrashlyticsHelper for detailed error reporting\n")
                append("2. Use ErrorHelper for standard error handling\n")
                append("3. Check Firebase Console for crash reports\n")
                append("4. Use filters in Firebase Console to categorize crashes\n")
            }

        infoTextView.text = info

        // Log mesajları
        val logs =
            buildString {
                append("=== Recent Logs ===\n\n")
                append("Logs are sent to Firebase Crashlytics.\n")
                append("Check Firebase Console for detailed logs.\n\n")
                append("Last log: ${System.currentTimeMillis()}\n")
            }

        logsTextView.text = logs
    }

    private fun testCrash() {
        Timber.e("Test crash triggered from debug panel")
        throw RuntimeException("Test crash from CrashlyticsDebugActivity")
    }

    private fun testNonFatalException() {
        try {
            val exception = IllegalStateException("Test non-fatal exception from debug panel")
            CrashlyticsHelper.recordNonFatalException(
                exception = exception,
                category = "test",
                priority = CrashlyticsHelper.Priority.LOW,
                tag = CrashlyticsHelper.Tags.UI,
                errorContext = "CrashlyticsDebugActivity.testNonFatalException",
            )
            showMessage("Test non-fatal exception sent to Crashlytics")
        } catch (e: Exception) {
            Timber.e(e, "Test exception gönderilemedi")
        }
    }

    private fun testLog() {
        CrashlyticsHelper.log("Test log message from debug panel", "INFO")
        showMessage("Test log sent to Crashlytics")
    }

    private fun addCustomKey() {
        try {
            val key = "test_key_${System.currentTimeMillis()}"
            val value = "test_value_${System.currentTimeMillis()}"
            CrashlyticsHelper.setCustomKey(key, value)
            showMessage("Custom key added: $key = $value")
        } catch (e: Exception) {
            Timber.e(e, "Custom key eklenemedi")
        }
    }

    private fun setUserFlow() {
        CrashlyticsHelper.setUserFlow(
            action = "test_action_from_debug_panel",
            screenName = "CrashlyticsDebugActivity",
        )
        showMessage("User flow set: test_action_from_debug_panel")
    }

    private fun clearLogs() {
        CrashlyticsHelper.clearLogs()
        LifecycleTracker.clearLogs()
        showMessage("Logs cleared (note: this doesn't clear Firebase Console logs)")
    }

    private fun sendLifecycleLogs() {
        val logs = LifecycleTracker.getLogs()
        if (logs.isEmpty()) {
            showMessage("Lifecycle log bulunamadı. Uygulamayı kullanarak log oluşturun.")
            return
        }

        LifecycleTracker.sendLogsToFirebase()
        showMessage("${logs.size} lifecycle log Firebase'e gönderildi")
        
        // Logları info text view'da göster
        val logsText = buildString {
            append("=== Lifecycle Logs (${logs.size} entries) ===\n\n")
            logs.forEach { entry: LifecycleTracker.LifecycleLogEntry ->
                append(entry.toString())
                append("\n")
            }
        }
        infoTextView.text = logsText
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
        Timber.d(message)
    }
}
