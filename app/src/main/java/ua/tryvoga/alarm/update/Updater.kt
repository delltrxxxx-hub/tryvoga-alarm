package ua.tryvoga.alarm.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ua.tryvoga.alarm.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Оновлення з GitHub Releases: перевірка останнього релізу, завантаження APK
 * і встановлення через системний PackageInstaller (без пошуку файлу вручну).
 */
object Updater {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    fun dismiss() { _state.value = UpdateState.Idle }

    /** 1.10 > 1.9 — порівнюємо числами, а не рядками. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val l = local.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    suspend fun check(silent: Boolean = false) {
        if (!silent) _state.value = UpdateState.Checking
        _state.value = try {
            val info = fetchLatest()
            if (info != null && isNewer(info.version, currentVersion)) UpdateState.Available(info)
            else if (silent) UpdateState.Idle else UpdateState.UpToDate
        } catch (e: Exception) {
            if (silent) UpdateState.Idle else UpdateState.Error("Не вдалося перевірити оновлення: ${e.message}")
        }
    }

    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val c = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "TryvogaAlarm/${BuildConfig.VERSION_NAME}")
        try {
            if (c.responseCode == 404) return@withContext null
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val assets = o.optJSONArray("assets") ?: return@withContext null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", true)) {
                    return@withContext UpdateInfo(
                        version = o.optString("tag_name").removePrefix("v"),
                        notes = o.optString("body").trim(),
                        apkUrl = a.getString("browser_download_url"),
                        size = a.optLong("size"),
                    )
                }
            }
            null
        } finally { c.disconnect() }
    }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Відкрити системний дозвіл «Встановлення невідомих застосунків» для цього застосунку. */
    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    suspend fun downloadAndInstall(ctx: Context, info: UpdateInfo) {
        try {
            val file = File(ctx.cacheDir, "update.apk")
            withContext(Dispatchers.IO) {
                var url = URL(info.apkUrl)
                var c: HttpURLConnection
                var redirects = 0
                while (true) { // GitHub віддає файл через перенаправлення
                    c = url.openConnection() as HttpURLConnection
                    c.instanceFollowRedirects = false
                    c.connectTimeout = 10000; c.readTimeout = 20000
                    c.setRequestProperty("User-Agent", "TryvogaAlarm/${BuildConfig.VERSION_NAME}")
                    val code = c.responseCode
                    if (code in 300..399 && redirects++ < 5) { url = URL(url, c.getHeaderField("Location")); c.disconnect(); continue }
                    if (code !in 200..299) throw IOException("HTTP $code")
                    break
                }
                val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
                c.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            if (total > 0) _state.value = UpdateState.Downloading(info, done.toFloat() / total)
                        }
                    }
                }
                c.disconnect()
            }
            _state.value = UpdateState.Installing
            withContext(Dispatchers.IO) { install(ctx, file) }
        } catch (e: Exception) {
            _state.value = UpdateState.Error("Помилка оновлення: ${e.message}")
        }
    }

    private fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            s.openWrite("app.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                s.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(ctx, 4242, Intent(ctx, InstallResultReceiver::class.java), flags)
            s.commit(pi.intentSender)
        }
    }

    internal fun onInstallFailed(msg: String) { _state.value = UpdateState.Error(msg) }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Система просить один дотик «Оновити» — показуємо її вікно.
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let {
                    context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // застосунок перезапуститься вже новою версією
            else -> Updater.onInstallFailed(
                "Встановлення не вдалося (код $status): " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "")
            )
        }
    }
}
