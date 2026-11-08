package app.shilex.android.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import app.shilex.android.Diag
import app.shilex.android.assistant.VoiceSession
import java.util.Locale

/**
 * The "watch my messages" scene: for a while, JARVIS stays quiet and says out loud only when
 * a person needs the user ([WatchRules]). Runs on the phone with its own voice; nothing goes
 * to Gemini, and nothing is kept after the watch ends.
 */
object MessageWatch {
    @Volatile var untilMillis = 0L
        private set
    private var rules = WatchRules()
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null

    val watching: Boolean get() = System.currentTimeMillis() < untilMillis

    fun start(minutes: Int) {
        untilMillis = System.currentTimeMillis() + minutes * 60_000L
        rules = WatchRules()
        Diag.i("watch: on for $minutes min")
    }

    fun stop() {
        untilMillis = 0L
        main.post(::shutDown)
        Diag.i("watch: off")
    }

    /** Called by [NotificationWatch] for every new notification. */
    fun posted(context: Context, n: Seen) {
        if (!watching || n.packageName == context.packageName) return
        val audio = context.getSystemService(AudioManager::class.java)
        val silent = audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
        val line = rules.decide(n, System.currentTimeMillis(), silent, VoiceSession.get(context).active) ?: return
        Diag.i("watch: speaking up")
        main.post { say(context.applicationContext, line) }
    }

    private fun say(context: Context, line: String) {
        tts?.let {
            setVoiceLanguage(it, line)
            it.speak(line, TextToSpeech.QUEUE_ADD, null, "watch")
            return
        }
        lateinit var voice: TextToSpeech
        voice = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Diag.w("watch: no voice")
                return@TextToSpeech
            }
            setVoiceLanguage(voice, line)
            voice.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            voice.speak(line, TextToSpeech.QUEUE_ADD, null, "watch")
        }
        tts = voice
    }

    private fun setVoiceLanguage(voice: TextToSpeech, line: String) {
        if (line.any { it in '\u0980'..'\u09FF' }) {
            val bnBd = Locale("bn", "BD")
            val res = voice.setLanguage(bnBd)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                val bnIn = Locale("bn", "IN")
                val res2 = voice.setLanguage(bnIn)
                if (res2 == TextToSpeech.LANG_MISSING_DATA || res2 == TextToSpeech.LANG_NOT_SUPPORTED) {
                    voice.language = Locale("en", "IN")
                }
            }
        } else {
            voice.language = Locale("en", "IN")
        }
    }

    private fun shutDown() {
        tts?.shutdown()
        tts = null
    }
}
