package com.pnr.tv.util

import android.app.Activity
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.pnr.tv.util.ui.BackgroundManager
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Uygulama ve Activity lifecycle durumlarını izleyen ve loglayan sınıf.
 * Özellikle TV'de uygulama arka plana gidip tekrar açıldığında arka plan yükleme sorunlarını tespit etmek için kullanılır.
 *
 * Bu sınıf şunları izler:
 * - Application lifecycle (ProcessLifecycleOwner)
 * - Activity lifecycle (onCreate, onStart, onResume, onPause, onStop, onDestroy)
 * - Arka plan yükleme durumları
 * - Activity geçişleri (Player -> Main gibi)
 */
object LifecycleTracker {
    private val lifecycleLogs = ConcurrentLinkedQueue<LifecycleLogEntry>()
    private var isTrackingEnabled = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * Lifecycle log entry - her lifecycle event için bir kayıt
     */
    data class LifecycleLogEntry(
        val timestamp: Long,
        val timestampFormatted: String,
        val eventType: String, // "APP_START", "APP_STOP", "ACTIVITY_CREATE", "ACTIVITY_START", etc.
        val activityName: String? = null,
        val backgroundStatus: String? = null, // "LOADING", "LOADED", "FAILED", "CACHED", "NULL"
        val additionalInfo: String? = null,
    ) {
        override fun toString(): String {
            return buildString {
                append("[$timestampFormatted] ")
                append("[$eventType] ")
                activityName?.let { append("Activity: $it | ") }
                backgroundStatus?.let { append("Background: $it | ") }
                additionalInfo?.let { append("Info: $it") }
            }
        }
    }

    /**
     * Lifecycle tracking'i başlatır.
     * Application class'ta çağrılmalıdır.
     */
    fun startTracking() {
        if (isTrackingEnabled) {
            Timber.tag("LIFECYCLE_TRACKER").w("Tracking zaten aktif")
            return
        }

        isTrackingEnabled = true
        lifecycleLogs.clear()

        // Application lifecycle'ı izle
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    when (event) {
                        Lifecycle.Event.ON_START -> {
                            logAppEvent("APP_START", "Uygulama foreground'a geçti")
                        }
                        Lifecycle.Event.ON_STOP -> {
                            logAppEvent("APP_STOP", "Uygulama background'a geçti")
                        }
                        else -> {
                            // Diğer event'leri logla
                            logAppEvent("APP_${event.name}", null)
                        }
                    }
                }
            },
        )

        logAppEvent("TRACKING_STARTED", "Lifecycle tracking başlatıldı")
        Timber.tag("LIFECYCLE_TRACKER").d("✅ Lifecycle tracking başlatıldı")
    }

    /**
     * Activity için lifecycle observer oluşturur.
     * BaseActivity'de kullanılmalıdır.
     */
    fun createActivityObserver(activity: Activity): LifecycleEventObserver {
        return object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (!isTrackingEnabled) return

                val activityName = activity.javaClass.simpleName
                val backgroundStatus = getBackgroundStatus()

                when (event) {
                    Lifecycle.Event.ON_CREATE -> {
                        logActivityEvent(activityName, "ACTIVITY_CREATE", backgroundStatus, "Activity oluşturuldu")
                    }
                    Lifecycle.Event.ON_START -> {
                        logActivityEvent(activityName, "ACTIVITY_START", backgroundStatus, "Activity başlatıldı")
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        logActivityEvent(activityName, "ACTIVITY_RESUME", backgroundStatus, "Activity resume edildi")
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        logActivityEvent(activityName, "ACTIVITY_PAUSE", backgroundStatus, "Activity pause edildi")
                    }
                    Lifecycle.Event.ON_STOP -> {
                        logActivityEvent(activityName, "ACTIVITY_STOP", backgroundStatus, "Activity durduruldu")
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        logActivityEvent(activityName, "ACTIVITY_DESTROY", backgroundStatus, "Activity yok edildi")
                    }
                    else -> {
                        // Diğer event'leri logla
                        logActivityEvent(activityName, "ACTIVITY_${event.name}", backgroundStatus, null)
                    }
                }
            }
        }
    }

    /**
     * Arka plan durumunu kontrol eder ve loglar.
     */
    fun logBackgroundCheck(activityName: String, context: String) {
        if (!isTrackingEnabled) return

        val backgroundStatus = getBackgroundStatus()
        val timestamp = System.currentTimeMillis()
        val timestampFormatted = dateFormat.format(Date(timestamp))

        lifecycleLogs.offer(
            LifecycleLogEntry(
                timestamp = timestamp,
                timestampFormatted = timestampFormatted,
                eventType = "BACKGROUND_CHECK",
                activityName = activityName,
                backgroundStatus = backgroundStatus,
                additionalInfo = context,
            ),
        )

        Timber.tag("LIFECYCLE_TRACKER").d("📊 Background Check [$activityName]: $backgroundStatus - $context")
    }

    /**
     * Application seviyesinde event loglar.
     */
    private fun logAppEvent(eventType: String, additionalInfo: String?) {
        if (!isTrackingEnabled) return

        val timestamp = System.currentTimeMillis()
        val timestampFormatted = dateFormat.format(Date(timestamp))

        lifecycleLogs.offer(
            LifecycleLogEntry(
                timestamp = timestamp,
                timestampFormatted = timestampFormatted,
                eventType = eventType,
                activityName = null,
                backgroundStatus = getBackgroundStatus(),
                additionalInfo = additionalInfo,
            ),
        )

        Timber.tag("LIFECYCLE_TRACKER").d("📊 App Event: $eventType${additionalInfo?.let { " - $it" } ?: ""}")
    }

    /**
     * Activity seviyesinde event loglar.
     */
    private fun logActivityEvent(
        activityName: String,
        eventType: String,
        backgroundStatus: String?,
        additionalInfo: String?,
    ) {
        if (!isTrackingEnabled) return

        val timestamp = System.currentTimeMillis()
        val timestampFormatted = dateFormat.format(Date(timestamp))

        lifecycleLogs.offer(
            LifecycleLogEntry(
                timestamp = timestamp,
                timestampFormatted = timestampFormatted,
                eventType = eventType,
                activityName = activityName,
                backgroundStatus = backgroundStatus,
                additionalInfo = additionalInfo,
            ),
        )

        Timber.tag("LIFECYCLE_TRACKER").d("📊 Activity Event [$activityName]: $eventType | Background: ${backgroundStatus ?: "N/A"}${additionalInfo?.let { " | $it" } ?: ""}")
    }

    /**
     * Arka plan durumunu kontrol eder.
     */
    private fun getBackgroundStatus(): String? {
        return try {
            val cached = BackgroundManager.getCachedBackground()
            when {
                cached == null -> "NULL"
                cached is android.graphics.drawable.BitmapDrawable -> {
                    val bitmap = cached.bitmap
                    if (bitmap == null || bitmap.isRecycled) {
                        "RECYCLED"
                    } else {
                        "CACHED"
                    }
                }
                else -> "LOADED"
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Toplanan logları alır.
     */
    fun getLogs(): List<LifecycleLogEntry> {
        return lifecycleLogs.toList()
    }

    /**
     * Logları temizler.
     */
    fun clearLogs() {
        lifecycleLogs.clear()
        logAppEvent("LOGS_CLEARED", "Loglar temizlendi")
        Timber.tag("LIFECYCLE_TRACKER").d("🗑️ Loglar temizlendi")
    }

    /**
     * Logları Firebase Crashlytics'e gönderir.
     */
    fun sendLogsToFirebase() {
        if (!isTrackingEnabled) {
            Timber.tag("LIFECYCLE_TRACKER").w("⚠️ Tracking aktif değil, log gönderilemiyor")
            return
        }

        val logs = getLogs()
        if (logs.isEmpty()) {
            CrashlyticsHelper.log("LifecycleTracker: Gönderilecek log yok", "WARN")
            CrashlyticsHelper.sendUnsentReports()
            return
        }

        try {
            // Log sayısını gönder
            CrashlyticsHelper.setCustomKey("lifecycle_log_count", logs.size)
            CrashlyticsHelper.setCustomKey("lifecycle_tracking_enabled", isTrackingEnabled)

            // Her log entry'yi Firebase'e gönder
            logs.forEachIndexed { index, entry ->
                CrashlyticsHelper.setCustomKey("lifecycle_log_${index}_event", entry.eventType)
                CrashlyticsHelper.setCustomKey("lifecycle_log_${index}_activity", entry.activityName ?: "N/A")
                CrashlyticsHelper.setCustomKey("lifecycle_log_${index}_background", entry.backgroundStatus ?: "N/A")
                CrashlyticsHelper.setCustomKey("lifecycle_log_${index}_timestamp", entry.timestamp)
                entry.additionalInfo?.let {
                    CrashlyticsHelper.setCustomKey("lifecycle_log_${index}_info", it)
                }
            }

            // Tüm logları birleştirilmiş string olarak gönder
            val logsSummary = logs.joinToString("\n") { it.toString() }
            CrashlyticsHelper.log("=== LIFECYCLE LOGS START ===\n$logsSummary\n=== LIFECYCLE LOGS END ===", "INFO")

            // Hemen gönder
            CrashlyticsHelper.sendUnsentReports()

            Timber.tag("LIFECYCLE_TRACKER").d("✅ ${logs.size} lifecycle log Firebase'e gönderildi")
        } catch (e: Exception) {
            Timber.tag("LIFECYCLE_TRACKER").e(e, "❌ Lifecycle logları Firebase'e gönderilemedi")
            CrashlyticsHelper.recordNonFatalException(
                exception = e,
                category = "LIFECYCLE_TRACKER_ERROR",
                priority = CrashlyticsHelper.Priority.MEDIUM,
                tag = CrashlyticsHelper.Tags.BACKGROUND,
                errorContext = "LifecycleTracker.sendLogsToFirebase",
            )
        }
    }

    /**
     * Tracking durumunu kontrol eder.
     */
    fun isTracking(): Boolean {
        return isTrackingEnabled
    }
}
