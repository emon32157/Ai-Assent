package app.shilex.android.assistant

import android.app.Application
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import app.shilex.android.data.Settings
import app.shilex.android.learn.Habits
import app.shilex.android.local.ActivityLog
import app.shilex.android.local.LocalCommand
import app.shilex.android.local.LocalPhase
import app.shilex.android.local.LocalTurn
import app.shilex.android.task.Task
import app.shilex.android.task.TaskTracker
import app.shilex.android.wake.VoiceProfile
import app.shilex.android.wake.VoiceSetup
import app.shilex.android.wake.WakeWordModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The screen's handle on the voice session, which outlives the screen. */
class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val session = VoiceSession.get(app)

    val state: StateFlow<UiState> = session.state
    val level: StateFlow<Float> = session.level
    val task: StateFlow<Task?> = TaskTracker.current
    private val settings = Settings(app)
    private val local = LocalTurn.get(app)

    val localPhase: StateFlow<LocalPhase> = local.phase

    private val _wakeWord = MutableStateFlow(settings.wakeWord)

    /** The wake-word setting, live (Settings screen or the notification's Turn off). */
    val wakeWord: StateFlow<Boolean> = _wakeWord.asStateFlow()
    // Android holds preference listeners weakly, so keep this one referenced.
    private val watcher = settings.watch { _wakeWord.value = settings.wakeWord }

    override fun onCleared() {
        settings.unwatch(watcher)
    }
    val activity: StateFlow<List<ActivityLog.Entry>> = ActivityLog.entries

    /** Home's quick actions: run on the phone at once; returns the reply to show. */
    fun quick(command: LocalCommand): String = local.runNow(command)

    fun clearActivity() = ActivityLog.clear()

    private val habits get() = Habits.get(getApplication())

    var learning: Boolean
        get() = settings.learning
        set(value) { settings.learning = value }

    /** Home's quick actions: the user's most used phone commands, once there are some. */
    fun favourites(): List<LocalCommand> = if (settings.learning) habits.top(4) else emptyList()

    /** What has been learned, in plain words for Settings. */
    fun learnedSummary(): String {
        val summary = habits.summary(java.time.LocalTime.now().hour)
        val phrases = habits.learnedPhrases()
        return listOfNotNull(
            summary.ifEmpty { null },
            if (phrases > 0) "$phrases of your own phrases now run on the phone." else null,
        ).joinToString(" ").ifEmpty { "Nothing yet." }
    }

    fun forgetLearned() = habits.forget()

    var onboarded: Boolean
        get() = settings.onboarded
        set(value) { settings.onboarded = value }

    var replyLanguage: String
        get() = settings.replyLanguage
        set(value) { settings.replyLanguage = value }

    var model: String
        get() = settings.model
        set(value) { settings.model = value }

    var localCommands: Boolean
        get() = settings.localCommands
        set(value) { settings.localCommands = value }

    var talkOver: Boolean
        get() = settings.talkOver
        set(value) { settings.talkOver = value }

    var waveToStop: Boolean
        get() = settings.waveToStop
        set(value) { settings.waveToStop = value }

    var networkSpeech: Boolean
        get() = settings.networkSpeech
        set(value) { settings.networkSpeech = value }

    /** "on-device", "offline", "network" or null (Gemini hears everything). */
    val speechEngine: String? get() = local.recogniser()

    /** Samsung and others stop background apps unless they're exempt from battery saving. */
    val batteryExempt: Boolean
        get() = getApplication<Application>().getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(getApplication<Application>().packageName)

    suspend fun saveKey(key: String): String? = session.saveKey(key)

    fun forgetKey() = session.forgetKey()

    fun start() = session.start()

    fun stop() = session.stop("Stop button")

    fun interrupt() = session.interrupt()

    fun permissionAnswered(granted: Boolean) = session.permissionAnswered(granted)

    fun micUnavailable() = session.micUnavailable()

    /** Only debug builds carry the (non-commercial) wake-word model. */
    val wakeWordAvailable: Boolean = WakeWordModel.available(app)

    val wakeWordOn: Boolean get() = Settings(getApplication()).wakeWord

    val voiceSaved: Boolean get() = VoiceProfile.exists(getApplication())

    /** Voice setup: one sample. Pauses the wake listener and any conversation meanwhile. */
    suspend fun recordVoiceSample(speakNow: () -> Unit): VoiceSetup.Sample {
        session.stop("voice setup")
        VoiceService.voiceSetup(true)
        delay(MIC_HANDOVER_MS) // let the other mic users close first
        return try {
            VoiceSetup.record(getApplication(), speakNow)
        } finally {
            VoiceService.voiceSetup(false)
        }
    }

    fun saveVoice(windows: List<FloatArray>) {
        VoiceProfile.save(getApplication(), VoiceProfile.create(windows))
        VoiceService.voiceSetup(false) // restarts the listener with the new profile
    }

    fun forgetVoice() {
        VoiceProfile.forget(getApplication())
        VoiceService.voiceSetup(false)
    }

    /** Call while the screen is visible: turning it on starts the listening service. */
    fun setWakeWord(on: Boolean) {
        Settings(getApplication()).wakeWord = on
        VoiceService.start(getApplication())
    }

    private companion object {
        const val MIC_HANDOVER_MS = 400L
    }
}
