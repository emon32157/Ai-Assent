package app.shilex.android.local

import app.shilex.android.local.LocalCommand.Alarm
import app.shilex.android.local.LocalCommand.Media
import app.shilex.android.local.LocalCommand.Mute
import app.shilex.android.local.LocalCommand.OpenApp
import app.shilex.android.local.LocalCommand.SetVolume
import app.shilex.android.local.LocalCommand.StepVolume
import app.shilex.android.local.LocalCommand.Timer
import app.shilex.android.local.LocalCommand.Torch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalRouterTest {
    private fun check(expected: LocalCommand?, vararg phrases: String) =
        phrases.forEach { assertEquals(it, expected, LocalRouter.match(it)) }

    @Test
    fun opensAppsInEnglish() {
        check(OpenApp("youtube"), "Hey Jarvis, open YouTube.", "open YouTube", "start youtube", "launch YouTube")
        check(OpenApp("calendar"), "open Calendar", "open the calendar app", "start calendar")
        check(OpenApp("google maps"), "open Google Maps")
        check(OpenApp("camera"), "launch camera", "open camera")
    }

    @Test
    fun volume() {
        check(SetVolume(30), "set volume to 30", "volume 30%", "volume 30 percent")
        check(SetVolume(100), "volume full", "volume max")
        check(StepVolume(true), "volume up", "increase volume")
        check(StepVolume(false), "volume down", "lower the volume", "decrease volume")
        check(Mute(true), "mute", "silent mode")
        check(Mute(false), "unmute", "phone unmute")
    }

    @Test
    fun mediaAndTorch() {
        check(Media(MediaKey.Pause), "pause", "music pause", "stop music")
        check(Media(MediaKey.Play), "resume", "play music", "play song")
        check(Media(MediaKey.Next), "next song", "next", "skip")
        check(Media(MediaKey.Previous), "previous song", "previous track")
        check(Torch(true), "flashlight on", "turn on the flashlight", "start torch")
        check(Torch(false), "flashlight off", "torch off")
    }

    @Test
    fun clockAndStatus() {
        check(LocalCommand.Time, "what's the time", "time")
        check(LocalCommand.Date, "what day is it today", "date", "which day is today")
        check(LocalCommand.Battery, "battery level", "is my phone charging", "battery")
        check(Timer(300), "set a timer for 5 minutes", "timer 5 minutes")
        check(Timer(5_400), "timer for 1 hour 30 minutes")
        check(Alarm(6, 30, Period.Am), "alarm at 6:30 a.m.", "alarm at 6:30 am")
        check(Alarm(7, 0, Period.Pm), "alarm at 7 pm")
        check(Alarm(6, 0, null), "alarm at 6")
        check(Alarm(18, 15, null), "alarm for 18:15")
        check(LocalCommand.Stop, "stop", "cancel", "never mind", "go to sleep", "standby")
    }

    @Test
    fun leavesTheRestToGemini() {
        assertNull(LocalRouter.match("play despacito"))
        assertNull(LocalRouter.match("open WhatsApp and message Rahul I'm late"))
        assertNull(LocalRouter.match("call mom"))
        assertNull(LocalRouter.match("what's the time in London"))
        assertNull(LocalRouter.match("turn on battery saver"))
        assertNull(LocalRouter.match("cancel my alarm"))
        assertNull(LocalRouter.match("open ChatGPT and start voice"))
        assertNull(LocalRouter.match("explain quantum computing"))
        assertNull(LocalRouter.match("torch"))
        assertNull(LocalRouter.match(""))
    }

    @Test
    fun bengaliSpeechHandsOffToGemini() {
        assertNull(LocalRouter.match("ইউটিউব খোলো"))
        assertNull(LocalRouter.match("গান থামাও"))
    }

    @Test
    fun understandsTheOfflineRecognisersSpelling() {
        check(OpenApp("whatsapp"), "the open what's up")
        check(OpenApp("youtube"), "open you tube")
        check(Torch(true), "flashlight on")
        check(Torch(false), "flashlight off")
        check(Media(MediaKey.Pause), "music pause")
        check(SetVolume(30), "volume thirty percent")
        check(SetVolume(45), "set the volume to forty five percent")
        check(Timer(300), "set a timer for five minutes")
        check(Alarm(6, 30, Period.Am), "set an alarm for six thirty am")
        check(LocalCommand.Battery, "battery level")
        check(LocalCommand.Time, "what time is it")
    }

    @Test
    fun spokenNumbers() {
        assertEquals("volume 35 percent", LocalRouter.numbers("volume thirty five percent"))
        assertEquals("100", LocalRouter.numbers("one hundred"))
        assertEquals("6 30", LocalRouter.numbers("six thirty"))
    }

    @Test
    fun triesEveryAlternative() {
        assertEquals(OpenApp("youtube"), LocalRouter.match(listOf("not matching", "open youtube")))
    }
}
