package app.shilex.android.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.shilex.android.Branding
import app.shilex.android.R
import app.shilex.android.assistant.AssistantViewModel
import app.shilex.android.assistant.Phase
import app.shilex.android.wake.WakeWordModel

private enum class Step { Welcome, Key, LocalCloud, Mic, Wake, Control, Done }

/**
 * First run: welcome, the user's Gemini key (required), what runs where, the microphone,
 * the wake word and the optional phone control. Optional steps can be skipped.
 */
@Composable
fun Onboarding(assistant: AssistantViewModel, hasKey: Boolean, onFinished: () -> Unit) {
    // Coming back for a new key only: skip the introduction.
    var index by rememberSaveable { mutableIntStateOf(if (assistant.onboarded) Step.Key.ordinal else 0) }
    val step = Step.entries[index]
    val next: () -> Unit = { if (index < Step.entries.lastIndex) index++ else onFinished() }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Progress(index, Modifier.padding(top = 20.dp, bottom = 8.dp))
        AnimatedContent(step, label = "step", modifier = Modifier.weight(1f)) { shown ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 24.dp)) {
                when (shown) {
                    Step.Welcome -> Page(Branding.ASSISTANT_NAME, stringResource(R.string.onboarding_welcome_desc)) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Orb(Phase.Listening, Modifier.size(180.dp))
                        }
                        Primary(stringResource(R.string.onboarding_get_started), next)
                    }
                    Step.Key -> Page(stringResource(R.string.onboarding_key_title), stringResource(R.string.onboarding_key_desc, Branding.ASSISTANT_NAME)) {
                        if (hasKey) {
                            Text(stringResource(R.string.onboarding_key_saved), color = Accent, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(16.dp))
                            Primary(stringResource(R.string.onboarding_continue), next)
                        } else {
                            KeyForm(onSave = { key -> assistant.saveKey(key).also { if (it == null) next() } })
                        }
                    }
                    Step.LocalCloud -> Page(stringResource(R.string.onboarding_where_title), stringResource(R.string.onboarding_where_desc)) {
                        Section(null) {
                            InfoRow(stringResource(R.string.onboarding_local_title), stringResource(R.string.onboarding_local_desc)) {
                                Pill(stringResource(R.string.badge_local), Accent)
                            }
                            InfoRow(stringResource(R.string.onboarding_gemini_title), stringResource(R.string.onboarding_gemini_desc)) {
                                Pill(stringResource(R.string.badge_gemini), Warning)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Primary(stringResource(R.string.onboarding_continue), next)
                    }
                    Step.Mic -> MicStep(next)
                    Step.Wake -> Page(stringResource(R.string.onboarding_handsfree_title), stringResource(R.string.onboarding_handsfree_desc, WakeWordModel.PHRASE)) {
                        if (assistant.wakeWordAvailable && micAllowed()) {
                            var on by remember { mutableStateOf(assistant.wakeWordOn) }
                            Section(null) {
                                SwitchRow(stringResource(R.string.wake_word), stringResource(R.string.onboarding_wake_desc), on) {
                                    on = it
                                    assistant.setWakeWord(it)
                                }
                                VoiceSetupRow(assistant.voiceSaved, assistant::recordVoiceSample, assistant::saveVoice, assistant::forgetVoice)
                            }
                        } else {
                            Text(
                                if (assistant.wakeWordAvailable) stringResource(R.string.onboarding_mic_first) else stringResource(R.string.onboarding_not_in_build),
                                color = Muted,
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Primary(stringResource(R.string.onboarding_continue), next)
                    }
                    Step.Control -> Page(stringResource(R.string.onboarding_control_title), stringResource(R.string.onboarding_control_desc, Branding.ASSISTANT_NAME)) {
                        ControlCard(showWhenOn = true)
                        Spacer(Modifier.height(24.dp))
                        Primary(stringResource(R.string.onboarding_continue), next)
                        TextButton(onClick = next, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_skip)) }
                    }
                    Step.Done -> Page(stringResource(R.string.onboarding_done_title), stringResource(R.string.onboarding_done_desc, WakeWordModel.PHRASE)) {
                        Spacer(Modifier.height(16.dp))
                        Primary(stringResource(R.string.onboarding_start), onFinished)
                    }
                }
            }
        }
    }
}

@Composable
private fun MicStep(next: () -> Unit) {
    var allowed by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    val already = micAllowed()
    Page(stringResource(R.string.onboarding_mic_title), stringResource(R.string.onboarding_mic_desc)) {
        if (already || allowed) {
            Text(stringResource(R.string.onboarding_mic_allowed), color = Accent, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Primary(stringResource(R.string.onboarding_continue), next)
        } else {
            Primary(stringResource(R.string.onboarding_allow_mic)) { ask.launch(Manifest.permission.RECORD_AUDIO) }
            TextButton(onClick = next, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_not_now)) }
        }
    }
}

@Composable
private fun micAllowed(): Boolean =
    ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Composable
private fun Page(title: String, detail: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.displaySmall)
    Text(detail, style = MaterialTheme.typography.bodyLarge, color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
    Column(content = content)
}

@Composable
private fun Primary(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(label) }
}

@Composable
private fun Progress(index: Int, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Step.entries.forEach {
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(if (it.ordinal <= index) Accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
            )
        }
    }
}
