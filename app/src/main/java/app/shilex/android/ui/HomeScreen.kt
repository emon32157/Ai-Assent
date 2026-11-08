package app.shilex.android.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.shilex.android.Branding
import app.shilex.android.R
import app.shilex.android.assistant.Line
import app.shilex.android.assistant.Phase
import app.shilex.android.assistant.UiState
import app.shilex.android.learn.HabitKeys
import app.shilex.android.local.ActivityLog
import app.shilex.android.local.LocalCommand
import app.shilex.android.local.LocalPhase
import app.shilex.android.local.MediaKey
import app.shilex.android.task.Task
import app.shilex.android.wake.WakeWordModel

/** What the assistant is doing, in words and a colour, from the session and the local turn. */
private data class Status(val title: String, val hint: String, val badge: String, val orb: Phase, val live: Boolean)

private fun status(context: Context, state: UiState, local: LocalPhase, wakeOn: Boolean): Status = when {
    local == LocalPhase.Listening -> Status(
        context.getString(R.string.status_listening),
        context.getString(R.string.status_hint_local_listening),
        context.getString(R.string.badge_on_phone),
        Phase.Listening,
        true,
    )
    local == LocalPhase.Doing -> Status(
        context.getString(R.string.status_working),
        context.getString(R.string.status_hint_local_doing),
        context.getString(R.string.badge_on_phone),
        Phase.Connecting,
        true,
    )
    local == LocalPhase.Speaking -> Status(
        context.getString(R.string.status_speaking),
        context.getString(R.string.status_hint_local_speaking),
        context.getString(R.string.badge_on_phone),
        Phase.Speaking,
        true,
    )
    state.phase == Phase.Connecting -> Status(
        context.getString(R.string.status_thinking),
        context.getString(R.string.status_hint_connecting),
        context.getString(R.string.badge_gemini),
        Phase.Connecting,
        true,
    )
    state.phase == Phase.Listening -> Status(
        context.getString(R.string.status_listening),
        context.getString(R.string.status_hint_gemini_listening),
        context.getString(R.string.badge_gemini),
        Phase.Listening,
        true,
    )
    state.phase == Phase.Speaking -> Status(
        context.getString(R.string.status_speaking),
        context.getString(R.string.status_hint_speaking),
        context.getString(R.string.badge_gemini),
        Phase.Speaking,
        true,
    )
    else -> Status(
        context.getString(R.string.status_standby),
        state.problem ?: if (wakeOn) context.getString(R.string.status_hint_standby_wake, WakeWordModel.PHRASE) else context.getString(R.string.status_hint_standby_tap),
        if (wakeOn) context.getString(R.string.badge_wake_on) else context.getString(R.string.badge_wake_off),
        Phase.Off,
        false,
    )
}

@Composable
fun HomeScreen(
    state: UiState,
    localPhase: LocalPhase,
    level: Float,
    task: Task?,
    activity: List<ActivityLog.Entry>,
    wakeOn: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onInterrupt: () -> Unit,
    onMicDenied: () -> Unit,
    onPermissionAnswered: (Boolean) -> Unit,
    onQuick: (LocalCommand) -> String,
    onSeeActivity: () -> Unit,
    favourites: List<LocalCommand> = emptyList(),
) {
    val context = LocalContext.current
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onStart() else onMicDenied()
    }
    // A tool needs Contacts or notifications: show Android's own Allow dialog.
    val askForTool = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onPermissionAnswered)
    LaunchedEffect(state.permissionNeeded) {
        state.permissionNeeded?.let(askForTool::launch)
    }
    val startListening = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) onStart() else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }
    val now = status(context, state, localPhase, wakeOn)
    val problem = state.problem != null && !now.live

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Branding.ASSISTANT_NAME, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Pill(now.badge, if (now.live) Accent else Muted)
        }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Orb(
                phase = now.orb,
                level = level,
                modifier = Modifier
                    .size(220.dp)
                    .semantics { contentDescription = context.getString(R.string.desc_orb_status, Branding.ASSISTANT_NAME, now.title) }
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        when (state.phase) {
                            Phase.Off -> if (localPhase == LocalPhase.Idle) startListening()
                            Phase.Speaking -> onInterrupt()
                            else -> Unit
                        }
                    },
            )
        }

        AnimatedContent(now.title, label = "status") { title ->
            Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.fillMaxWidth())
        }
        Text(
            now.hint,
            style = MaterialTheme.typography.bodyMedium,
            color = if (problem) Warning else Muted,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
        )

        // The task in progress, or one that just finished (2 minutes).
        val recentTask = task?.takeIf { now.live || System.currentTimeMillis() - it.at < TASK_SHOWN_MS }
        recentTask?.let { TaskCard(it, Modifier.padding(bottom = 12.dp)) }

        if (state.phase != Phase.Off && state.lines.isNotEmpty()) {
            Conversation(state.lines, Modifier.fillMaxWidth().weight(1f))
        } else {
            QuickActions(onQuick, favourites)
            Spacer(Modifier.height(16.dp))
            Recent(activity.take(2), onSeeActivity)
        }

        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            val live = now.live
            FilledIconButton(
                onClick = { if (live) onStop() else startListening() },
                modifier = Modifier.size(72.dp),
                colors = if (live) {
                    IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                } else {
                    IconButtonDefaults.filledIconButtonColors()
                },
            ) {
                Icon(
                    if (live) Icons.Filled.Close else MicIcon,
                    contentDescription = if (live) stringResource(R.string.action_stop) else stringResource(R.string.action_talk),
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

/** One-tap phone actions; they run here, with no network. */
@Composable
private fun QuickActions(onQuick: (LocalCommand) -> String, favourites: List<LocalCommand>) {
    var torchOn by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<String?>(null) }
    // Learned favourites first, topped up with the defaults.
    val learned = favourites.mapNotNull { command -> HabitKeys.key(command)?.let { HabitKeys.describe(it).replaceFirstChar(Char::uppercase) to { command } } }
    val torchOffLabel = stringResource(R.string.quick_torch_off)
    val torchOnLabel = stringResource(R.string.quick_torch)
    val batteryLabel = stringResource(R.string.quick_battery)
    val timeLabel = stringResource(R.string.quick_time)
    val pauseLabel = stringResource(R.string.quick_pause)
    val defaults = listOf(
        (if (torchOn) torchOffLabel else torchOnLabel) to {
            torchOn = !torchOn
            LocalCommand.Torch(torchOn)
        },
        batteryLabel to { LocalCommand.Battery },
        timeLabel to { LocalCommand.Time },
        pauseLabel to { LocalCommand.Media(MediaKey.Pause) },
    )
    val actions = (learned + defaults.filter { d -> learned.none { it.first.startsWith(d.first, ignoreCase = true) } }).take(4)
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            actions.forEach { (label, command) ->
                Box(
                    Modifier
                        .weight(1f)
                        .background(Surface, MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                        .clickable { answer = onQuick(command()) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
        answer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
    }
}

@Composable
private fun Recent(entries: List<ActivityLog.Entry>, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.recent_header), style = MaterialTheme.typography.titleSmall, color = Muted, modifier = Modifier.weight(1f).padding(start = 4.dp))
        TextButton(onClick = onSeeAll) { Text(stringResource(R.string.recent_see_all)) }
    }
    if (entries.isEmpty()) {
        Text(stringResource(R.string.recent_empty), style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(start = 4.dp))
    }
    entries.forEach { ActivityRow(it) }
}

@Composable
private fun Conversation(lines: List<Line>, modifier: Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(lines.size, lines.lastOrNull()?.text) {
        if (lines.isNotEmpty()) list.animateScrollToItem(lines.lastIndex)
    }
    LazyColumn(modifier, state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(lines, key = { it.id }) { line ->
            Box(Modifier.fillMaxWidth(), contentAlignment = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (line.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .background(if (line.fromUser) Accent else Surface, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
}

private const val TASK_SHOWN_MS = 2 * 60_000L
