package ua.tryvoga.alarm

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsRepo.init(this)
        AlarmRepo.init(this)
        TimerRepo.init(this)
        Locations.init(this)
        // Застосунок завжди українською (системні діалоги, формати дат) — незалежно від мови телефона.
        if (android.os.Build.VERSION.SDK_INT >= 33) runCatching {
            val lm = getSystemService(android.app.LocaleManager::class.java)
            if (lm.applicationLocales.isEmpty) lm.applicationLocales = android.os.LocaleList.forLanguageTags("uk-UA")
        }
        java.util.Locale.setDefault(UK)
        val nm = getSystemService(NotificationManager::class.java)
        val ring = NotificationChannel(CH_RING, "Будильник, що дзвонить", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val info = NotificationChannel(CH_INFO, "Тривоги та статус будильників", NotificationManager.IMPORTANCE_DEFAULT)
        val svc = NotificationChannel(CH_SERVICE, "Службові", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannels(listOf(ring, info, svc))
    }

    companion object {
        const val CH_RING = "ring"
        const val CH_INFO = "info"
        const val CH_SERVICE = "service"
    }
}
