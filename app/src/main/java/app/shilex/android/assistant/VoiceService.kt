package app.shilex.android.assistant

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.shilex.android.Branding
import app.shilex.android.Diag
import app.shilex.android.MainActivity
import app.shilex.android.R
import app.shilex.android.data.Settings
import app.shilex.android.local.LocalTurn
import app.shilex.android.wake.WakeListener
import app.shilex.android.wake.WakeWordModel

/**
 * Keeps JARVIS running while the user is in other apps, with the notification Android
 * requires. Two modes: a voice session (Gemini hears everything), or, between sessions,
 * the on-phone wake-word listener (nothing leaves the phone until the wake phrase).
 */
class VoiceService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var wake: WakeListener
    private var foreground = false

    // Voice setup records with its own mic; the wake listener waits meanwhile.
    @Volatile private var setupActive = false

    override fun onCreate() {
        super.onCreate()
        wake = WakeListener(this, ::onWake)
        instance = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                VoiceSession.get(this).stop("notification Stop")
            }
            ACTION_WAKE_OFF -> {
                Settings(this).wakeWord = false
                Diag.i("wake: turned off from the notification")
            }
        }
        update()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wake.stop()
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Session running → session mode; else wake word if it's on; else stop. */
    fun update() {
        if (setupActive) {
            wake.stop()
            return
        }
        if (VoiceSession.get(this).active || LocalTurn.get(this).active) {
            wake.stop()
            show(sessionNotification())
            return
        }
        if (wakeWordWanted(this)) {
            show(wakeNotification())
            // Let the session's mic close before the wake listener opens its own.
            main.postDelayed({
                if (!VoiceSession.get(this).active && !setupActive && !wake.start()) Diag.w("wake: couldn't start")
            }, MIC_HANDOVER_MS)
            return
        }
        wake.stop()
        if (!foreground) {
            // Android 14+ throws ForegroundServiceDidNotStartInTimeException if startForeground
            // was never called before stopping. Ensure foreground is established before stopSelf.
            show(wakeNotification())
        }
        stopSelf()
    }

    private fun onWake() {
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            Diag.i("wake: phone locked, ignored")
            return
        }
        runCatching { ToneGenerator(AudioManager.STREAM_SYSTEM, 70).startTone(ToneGenerator.TONE_PROP_BEEP, 150) }
        if (Settings(this).localCommands && LocalTurn.get(this).start()) return
        VoiceSession.get(this).start()
    }

    private fun show(notification: Notification) {
        if (!foreground) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            foreground = true
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun sessionNotification() = builder(CHANNEL)
        .setContentTitle(getString(R.string.notif_listening_title, Branding.ASSISTANT_NAME))
        .setContentText(getString(R.string.notif_listening_text))
        .addAction(0, getString(R.string.notif_listening_stop), action(ACTION_STOP, 1))
        .build()

    // Standby: Android requires a notification while the mic is on; this one stays silent,
    // folded at the bottom of the shade and out of the status bar.
    private fun wakeNotification() = builder(STANDBY_CHANNEL)
        .setContentTitle(getString(R.string.notif_wake_title, WakeWordModel.PHRASE))
        .setContentText(getString(R.string.notif_wake_text))
        .addAction(0, getString(R.string.notif_wake_turn_off), action(ACTION_WAKE_OFF, 2))
        .build()

    private fun builder(channel: String): NotificationCompat.Builder {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel_listening), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.notif_channel_listening_desc, Branding.ASSISTANT_NAME)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(STANDBY_CHANNEL, getString(R.string.notif_channel_standby), NotificationManager.IMPORTANCE_MIN).apply {
                description = "Required by Android while the wake word is on. Silent and minimized."
            },
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
    }

    private fun action(name: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, VoiceService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL = "listening"
        private const val STANDBY_CHANNEL = "wake_standby"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "app.shilex.android.STOP"
        private const val ACTION_WAKE_OFF = "app.shilex.android.WAKE_OFF"
        private const val MIC_HANDOVER_MS = 400L

        @Volatile private var instance: VoiceService? = null

        fun wakeWordWanted(context: Context): Boolean =
            Settings(context).wakeWord &&
                WakeWordModel.available(context) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        /**
         * Starts the service, or updates it if it's already running. A fresh start must
         * happen while the app is on screen (Android blocks starting a mic service from the
         * background); a running service can switch modes at any time.
         */
        fun start(context: Context) {
            instance?.let {
                it.update()
                return
            }
            // Nothing to run; starting would oblige a notification Android then crashes without.
            if (!VoiceSession.get(context).active && !LocalTurn.get(context).active && !wakeWordWanted(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, VoiceService::class.java)) }
                .onFailure { Diag.w("service: could not start (${it.javaClass.simpleName})") }
        }

        /** Pauses (or resumes) the wake listener while voice setup uses the mic. */
        fun voiceSetup(active: Boolean) {
            instance?.let {
                it.setupActive = active
                it.update()
            }
        }

        /** The session ended: go back to waiting for the wake word, or stop. */
        fun stop(context: Context) {
            instance?.update()
        }
    }
}
