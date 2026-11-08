package app.shilex.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shilex.android.Branding
import app.shilex.android.R
import app.shilex.android.assistant.AssistantViewModel
import app.shilex.android.data.Settings as AppSettings
import app.shilex.android.memory.MemoryStore
import app.shilex.android.notify.NotificationWatch
import app.shilex.android.orb.FloatingOrb
import app.shilex.android.wake.WakeWordModel

@Composable
fun SettingsScreen(assistant: AssistantViewModel) {
    val context = LocalContext.current
    val wakeOn by assistant.wakeWord.collectAsStateWithLifecycle()
    var localOn by remember { mutableStateOf(assistant.localCommands) }
    var networkOn by remember { mutableStateOf(assistant.networkSpeech) }
    var engine by remember { mutableStateOf(assistant.speechEngine) }
    var batteryExempt by remember { mutableStateOf(assistant.batteryExempt) }
    LifecycleResumeEffect(Unit) {
        batteryExempt = assistant.batteryExempt
        engine = assistant.speechEngine
        onPauseOrDispose { }
    }
    val open = { action: String -> context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))

        Section(stringResource(R.string.settings_sec_gemini)) {
            InfoRow(stringResource(R.string.settings_api_key_title), stringResource(R.string.settings_api_key_desc)) {
                TextButton(onClick = assistant::forgetKey) { Text(stringResource(R.string.action_replace)) }
            }
            var currentModel by remember { mutableStateOf(assistant.model) }
            var editingModel by remember { mutableStateOf(false) }
            InfoRow(stringResource(R.string.model), stringResource(R.string.settings_model_desc, currentModel)) {
                TextButton(onClick = { editingModel = true }) { Text(stringResource(R.string.action_change)) }
            }
            if (editingModel) {
                var customModel by remember { mutableStateOf(currentModel) }
                AlertDialog(
                    onDismissRequest = { editingModel = false },
                    title = { Text(stringResource(R.string.settings_model_dialog_title)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.settings_model_dialog_desc), style = MaterialTheme.typography.bodyMedium)
                            AppSettings.AVAILABLE_MODELS.forEach { preset ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { customModel = preset },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = (customModel == preset), onClick = { customModel = preset })
                                    Text(preset, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            OutlinedTextField(
                                value = customModel,
                                onValueChange = { customModel = it },
                                label = { Text(stringResource(R.string.settings_model_name_label)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val chosen = customModel.trim().ifEmpty { AppSettings.DEFAULT_MODEL }
                            assistant.model = chosen
                            currentModel = chosen
                            editingModel = false
                        }) { Text(stringResource(R.string.save)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingModel = false }) { Text(stringResource(R.string.cancel)) }
                    },
                )
            }
            InfoRow(stringResource(R.string.settings_default_assistant_title), stringResource(R.string.settings_default_assistant_desc)) {
                TextButton(onClick = { open(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS) }) { Text(stringResource(R.string.action_open)) }
            }
        }

        Section(stringResource(R.string.settings_sec_conversation)) {
            var currentLang by remember { mutableStateOf(assistant.replyLanguage) }
            var choosingLang by remember { mutableStateOf(false) }
            InfoRow(
                stringResource(R.string.reply_language),
                stringResource(R.string.settings_reply_lang_desc, Branding.ASSISTANT_NAME, currentLang.replaceFirstChar { it.uppercase() }),
            ) {
                TextButton(onClick = { choosingLang = true }) { Text(stringResource(R.string.action_change)) }
            }
            if (choosingLang) {
                AlertDialog(
                    onDismissRequest = { choosingLang = false },
                    title = { Text(stringResource(R.string.reply_language)) },
                    text = {
                        Column {
                            AppSettings.REPLY_LANGUAGES.forEach { lang ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        assistant.replyLanguage = lang
                                        currentLang = lang
                                        choosingLang = false
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = (currentLang == lang), onClick = {
                                        assistant.replyLanguage = lang
                                        currentLang = lang
                                        choosingLang = false
                                    })
                                    Text(lang.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { choosingLang = false }) { Text(stringResource(R.string.action_done)) }
                    },
                )
            }
            var talkOn by remember { mutableStateOf(assistant.talkOver) }
            SwitchRow(
                stringResource(R.string.settings_talk_over_title, Branding.ASSISTANT_NAME),
                stringResource(R.string.settings_talk_over_desc),
                talkOn,
            ) {
                talkOn = it
                assistant.talkOver = it
            }
            var waveOn by remember { mutableStateOf(assistant.waveToStop) }
            SwitchRow(
                stringResource(R.string.settings_wave_title),
                stringResource(R.string.settings_wave_desc, Branding.ASSISTANT_NAME),
                waveOn,
            ) {
                waveOn = it
                assistant.waveToStop = it
            }
        }

        Section(stringResource(R.string.settings_sec_orb)) {
            val prefs = remember { AppSettings(context) }
            var orbOn by remember { mutableStateOf(prefs.floatingOrb && FloatingOrb.allowed(context)) }
            LifecycleResumeEffect(Unit) {
                orbOn = prefs.floatingOrb && FloatingOrb.allowed(context)
                onPauseOrDispose { }
            }
            SwitchRow(
                stringResource(R.string.settings_orb_title, Branding.ASSISTANT_NAME),
                stringResource(R.string.settings_orb_desc, Branding.ASSISTANT_NAME),
                orbOn,
            ) { on ->
                prefs.floatingOrb = on
                orbOn = on && FloatingOrb.allowed(context)
                if (on && !FloatingOrb.allowed(context)) {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                FloatingOrb.sync(context)
            }
        }

        Section(stringResource(R.string.settings_sec_notifications)) {
            var notifyOn by remember { mutableStateOf(NotificationWatch.allowed(context)) }
            LifecycleResumeEffect(Unit) {
                notifyOn = NotificationWatch.allowed(context)
                onPauseOrDispose { }
            }
            InfoRow(
                stringResource(R.string.settings_miss_title),
                stringResource(
                    R.string.settings_miss_desc,
                    if (notifyOn) stringResource(R.string.settings_state_on) else stringResource(R.string.settings_state_off),
                ),
            ) {
                TextButton(onClick = { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) {
                    Text(if (notifyOn) stringResource(R.string.action_change) else stringResource(R.string.action_turn_on))
                }
            }
        }

        Section(stringResource(R.string.wake_word)) {
            if (assistant.wakeWordAvailable) {
                SwitchRow(
                    stringResource(R.string.settings_wake_title, WakeWordModel.PHRASE),
                    stringResource(R.string.settings_wake_desc),
                    wakeOn,
                ) { assistant.setWakeWord(it) }
                VoiceSetupRow(
                    saved = assistant.voiceSaved,
                    record = assistant::recordVoiceSample,
                    onSave = assistant::saveVoice,
                    onForget = assistant::forgetVoice,
                )
            } else {
                InfoRow(stringResource(R.string.settings_wake_not_build_title), stringResource(R.string.settings_wake_not_build_desc, Branding.ASSISTANT_NAME))
            }
        }

        Section(stringResource(R.string.settings_sec_local)) {
            SwitchRow(
                stringResource(R.string.settings_local_title),
                stringResource(R.string.settings_local_desc),
                localOn,
            ) {
                localOn = it
                assistant.localCommands = it
            }
            SwitchRow(
                stringResource(R.string.settings_network_title),
                stringResource(R.string.settings_network_desc),
                networkOn,
                enabled = localOn,
            ) {
                networkOn = it
                assistant.networkSpeech = it
                engine = assistant.speechEngine
            }
            InfoRow(stringResource(R.string.settings_speech_heard_title), engineText(context, engine))
        }

        Section(stringResource(R.string.settings_sec_learning)) {
            var learnOn by remember { mutableStateOf(assistant.learning) }
            var learned by remember { mutableStateOf(assistant.learnedSummary()) }
            SwitchRow(
                stringResource(R.string.settings_learn_title, Branding.ASSISTANT_NAME),
                stringResource(R.string.settings_learn_desc),
                learnOn,
            ) {
                learnOn = it
                assistant.learning = it
            }
            InfoRow(stringResource(R.string.settings_learned_title), learned) {
                TextButton(onClick = {
                    assistant.forgetLearned()
                    learned = assistant.learnedSummary()
                }) { Text(stringResource(R.string.action_forget)) }
            }
        }

        Section(stringResource(R.string.settings_sec_permissions)) {
            ControlCard(Modifier.padding(vertical = 8.dp), showWhenOn = true)
            InfoRow(
                stringResource(R.string.settings_battery_title),
                if (batteryExempt) stringResource(R.string.settings_battery_allowed) else stringResource(R.string.settings_battery_disallowed, Branding.ASSISTANT_NAME),
            ) {
                TextButton(onClick = { open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }) { Text(stringResource(R.string.action_open)) }
            }
        }

        Section(stringResource(R.string.settings_sec_privacy)) {
            InfoRow(
                stringResource(R.string.settings_privacy_leaves_title),
                stringResource(R.string.settings_privacy_leaves_desc),
            )
            InfoRow(stringResource(R.string.settings_standby_title), stringResource(R.string.settings_standby_desc))
            val memory = remember { MemoryStore(context) }
            var notes by remember { mutableStateOf(memory.notes().size) }
            LifecycleResumeEffect(Unit) {
                notes = memory.notes().size
                onPauseOrDispose { }
            }
            InfoRow(
                stringResource(R.string.settings_remembers_title, Branding.ASSISTANT_NAME),
                stringResource(R.string.settings_remembers_desc, notes),
            ) {
                if (notes > 0) TextButton(onClick = { memory.clearNotes().also { notes = 0 } }) { Text(stringResource(R.string.action_forget_all)) }
            }
        }

        Section(stringResource(R.string.settings_sec_about)) {
            InfoRow(Branding.ASSISTANT_NAME, stringResource(R.string.settings_version, versionName(context)))
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun engineText(context: Context, engine: String?) = when (engine) {
    "on-device" -> context.getString(R.string.engine_on_device)
    "offline" -> context.getString(R.string.engine_offline)
    "network" -> context.getString(R.string.engine_network)
    else -> context.getString(R.string.engine_none)
}

private fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
