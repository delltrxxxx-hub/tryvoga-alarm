package ua.tryvoga.alarm.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import ua.tryvoga.alarm.update.UpdateInfo
import ua.tryvoga.alarm.update.UpdateState
import ua.tryvoga.alarm.update.Updater

/** Вікно «Доступне оновлення»: одна кнопка — завантажити з GitHub і встановити. */
@Composable
fun UpdateDialogHost() {
    val st by Updater.state.collectAsState()
    val info: UpdateInfo = when (val s = st) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        else -> return
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloading = st as? UpdateState.Downloading
    val progress by animateFloatAsState(downloading?.progress ?: 0f, soft(), label = "upd")

    Dialog(onDismissRequest = { if (downloading == null) Updater.dismiss() }) {
        GlassSurface(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(sheetColor())) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(10.dp))
                Text("Доступне оновлення", color = onBg(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("${Updater.currentVersion}  →  ${info.version}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        info.notes, color = dimText(), fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                    )
                }
                Spacer(Modifier.height(18.dp))
                if (downloading != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
                    Spacer(Modifier.height(8.dp))
                    Text("Завантаження… ${(progress * 100).toInt()}%", color = dimText(), fontSize = 13.sp)
                } else {
                    Row {
                        GlassButton("Пізніше", { Updater.dismiss() }, Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        GlassButton("Оновити", {
                            if (!Updater.canInstall(ctx)) Updater.openInstallPermission(ctx)
                            else scope.launch { Updater.downloadAndInstall(ctx.applicationContext, info) }
                        }, Modifier.weight(1f), Icons.Rounded.SystemUpdate, primary = true)
                    }
                    if (!Updater.canInstall(ctx)) {
                        Spacer(Modifier.height(8.dp))
                        Text("Спершу дозвольте встановлення оновлень (одноразово), потім знову натисніть «Оновити».", color = dimText(), fontSize = 12.sp)
                    }
                    if (info.size > 0) Text("${"%.1f".format(info.size / 1_048_576f)} МБ · GitHub", color = dimText(), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
