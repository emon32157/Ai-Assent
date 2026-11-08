package app.shilex.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.shilex.android.R
import app.shilex.android.memory.MemoryRules
import app.shilex.android.memory.MemoryStore
import app.shilex.android.wake.WakeWordModel

private class Command(val titleRes: Int, val examplesRes: Int)

private val LOCAL = listOf(
    Command(R.string.cmd_volume, R.string.cmd_volume_eg),
    Command(R.string.cmd_music, R.string.cmd_music_eg),
    Command(R.string.cmd_flashlight, R.string.cmd_flashlight_eg),
    Command(R.string.cmd_open_apps, R.string.cmd_open_apps_eg),
    Command(R.string.cmd_time_date_battery, R.string.cmd_time_date_battery_eg),
    Command(R.string.cmd_timers_alarms, R.string.cmd_timers_alarms_eg),
    Command(R.string.cmd_stop, R.string.cmd_stop_eg),
)

private val TASKS = listOf(
    Command(R.string.cmd_whatsapp, R.string.cmd_whatsapp_eg),
    Command(R.string.cmd_movie_tickets, R.string.cmd_movie_tickets_eg),
    Command(R.string.cmd_calls, R.string.cmd_calls_eg),
)

private val CLOUD = listOf(
    Command(R.string.cmd_songs, R.string.cmd_songs_eg),
    Command(R.string.cmd_screen, R.string.cmd_screen_eg),
    Command(R.string.cmd_questions, R.string.cmd_questions_eg),
    Command(R.string.cmd_reminders, R.string.cmd_reminders_eg),
)

/** What can be done: on the phone, with the user's OK, or with Gemini. */
@Composable
fun AutomationsScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.automations_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.automations_subtitle, WakeWordModel.PHRASE),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Routines()
        Group(
            stringResource(R.string.group_on_phone_title),
            stringResource(R.string.group_on_phone_desc),
            Accent,
            stringResource(R.string.badge_local),
            LOCAL,
        )
        Group(
            stringResource(R.string.group_tasks_title),
            stringResource(R.string.group_tasks_desc),
            Warning,
            stringResource(R.string.badge_needs_you),
            TASKS,
        )
        Group(
            stringResource(R.string.group_cloud_title),
            stringResource(R.string.group_cloud_desc),
            Warning,
            stringResource(R.string.badge_gemini),
            CLOUD,
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** The user's routines: say the name to run them; made by voice ("make a routine called …"). */
@Composable
private fun Routines() {
    val context = LocalContext.current
    val store = remember { MemoryStore(context) }
    var routines by remember { mutableStateOf(store.routines()) }
    LifecycleResumeEffect(Unit) {
        routines = store.routines()
        onPauseOrDispose { }
    }
    Section(null) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.routines_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Text(
            stringResource(R.string.routines_description),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        routines.forEach { r ->
            InfoRow(r.name, r.steps.joinToString(" · ")) {
                if (r !== MemoryRules.GOOD_MORNING) {
                    TextButton(onClick = { store.deleteRoutine(r.name).also { routines = store.routines() } }) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, detail: String, color: Color, badge: String, commands: List<Command>) {
    Section(null) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Pill(badge, color)
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
        commands.forEach { InfoRow(stringResource(it.titleRes), stringResource(it.examplesRes)) }
    }
}
