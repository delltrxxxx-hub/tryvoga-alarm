package ua.tryvoga.alarm.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ua.tryvoga.alarm.AlarmRepo
import ua.tryvoga.alarm.AlarmScheduler
import ua.tryvoga.alarm.AlertApi
import ua.tryvoga.alarm.Locations
import ua.tryvoga.alarm.SettingsRepo
import ua.tryvoga.alarm.Snapshot
import ua.tryvoga.alarm.TimerRepo
import ua.tryvoga.alarm.widget.WidgetUpdater

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val settings = SettingsRepo.settings
    val alarms = AlarmRepo.alarms
    init { Locations.init(app); AlertApi.cached(app) }
    val snapshot: StateFlow<Snapshot?> = AlertApi.snapshot
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private var poll: Job? = null

    fun onStart() {
        if (poll?.isActive == true) return
        poll = viewModelScope.launch {
            while (isActive) { refresh(); delay(15_000) }
        }
    }

    fun onStop() { poll?.cancel() }

    suspend fun refresh() {
        loading.value = true
        try {
            AlertApi.fetch(getApplication())
            error.value = null
            WidgetUpdater.update(getApplication())
        } catch (e: Exception) {
            error.value = e.message ?: "Помилка мережі"
        } finally {
            loading.value = false
        }
    }

    fun refreshNow() { viewModelScope.launch { refresh() } }
}

class MainActivity : ComponentActivity() {
    private lateinit var vm: MainViewModel

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SettingsRepo.init(this); AlarmRepo.init(this); TimerRepo.init(this)
        AlarmScheduler.rescheduleAll(this)
        vm = ViewModelProvider(this)[MainViewModel::class.java]
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val s by vm.settings.collectAsState()
            AppTheme(s) { MainScreen(vm) }
        }
    }

    override fun onStart() { super.onStart(); vm.onStart() }
    override fun onStop() { vm.onStop(); super.onStop() }
}
