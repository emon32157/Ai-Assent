package app.shilex.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.shilex.android.R
import app.shilex.android.wake.VoiceProfile
import app.shilex.android.wake.VoiceSetup
import app.shilex.android.wake.WakeWordModel
import kotlinx.coroutines.launch

/** "Teach my voice": five recordings of the wake phrase, kept only on this phone. */
@Composable
fun VoiceSetupDialog(
    record: suspend (speakNow: () -> Unit) -> VoiceSetup.Sample,
    onSave: (List<FloatArray>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val samples = remember { mutableStateListOf<FloatArray>() }
    var status by remember { mutableStateOf(context.getString(R.string.voice_setup_instruction, WakeWordModel.PHRASE)) }
    var busy by remember { mutableStateOf(false) }
    val done = samples.size >= VoiceProfile.SAMPLES

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.voice_setup_title, WakeWordModel.PHRASE)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.voice_setup_sample_progress, minOf(samples.size + 1, VoiceProfile.SAMPLES), VoiceProfile.SAMPLES),
                    color = Accent,
                )
                Text(status, modifier = Modifier.padding(top = 8.dp))
                Text(
                    stringResource(R.string.voice_setup_privacy),
                    color = Muted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            if (done) {
                Button(onClick = {
                    onSave(samples.toList())
                    onDismiss()
                }) { Text(stringResource(R.string.save)) }
            } else {
                Button(enabled = !busy, onClick = {
                    busy = true
                    status = context.getString(R.string.voice_setup_ready)
                    scope.launch {
                        val sample = record { status = context.getString(R.string.voice_setup_say_now, WakeWordModel.PHRASE) }
                        busy = false
                        status = when (sample) {
                            is VoiceSetup.Sample.Ok -> {
                                samples += sample.window
                                if (samples.size >= VoiceProfile.SAMPLES) {
                                    context.getString(R.string.voice_setup_got_all, VoiceProfile.SAMPLES)
                                } else {
                                    context.getString(R.string.voice_setup_got_one)
                                }
                            }
                            is VoiceSetup.Sample.Problem -> sample.message
                        }
                    }
                }) { Text(if (busy) stringResource(R.string.voice_setup_listening) else stringResource(R.string.voice_setup_record)) }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Under the wake-word switch: teach / redo / forget the user's voice. */
@Composable
fun VoiceSetupRow(
    saved: Boolean,
    record: suspend (speakNow: () -> Unit) -> VoiceSetup.Sample,
    onSave: (List<FloatArray>) -> Unit,
    onForget: () -> Unit,
) {
    var hasVoice by remember { mutableStateOf(saved) }
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (hasVoice) stringResource(R.string.voice_setup_saved) else stringResource(R.string.voice_setup_not_saved),
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { open = true }) {
            Text(if (hasVoice) stringResource(R.string.voice_setup_redo) else stringResource(R.string.voice_setup_teach), color = Accent)
        }
        if (hasVoice) {
            TextButton(onClick = {
                onForget()
                hasVoice = false
            }) { Text(stringResource(R.string.action_forget), color = Muted) }
        }
    }
    if (open) {
        VoiceSetupDialog(
            record = record,
            onSave = {
                onSave(it)
                hasVoice = true
            },
            onDismiss = { open = false },
        )
    }
}
