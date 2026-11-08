package app.shilex.android.live

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class LiveClientTest {
    private val setup = LiveProtocol.Setup(
        model = "gemini-2.0-flash",
        systemInstruction = "Test",
        voice = "Puck",
        languages = listOf("en-IN"),
        resumeHandle = null,
    )

    private class FakeWebSocket : WebSocket {
        var sent = mutableListOf<String>()
        var closed = false
        override fun cancel() {}
        override fun close(code: Int, reason: String?): Boolean { closed = true; return true }
        override fun queueSize(): Long = 0
        override fun request(): Request = Request.Builder().url("https://example.com").build()
        override fun send(text: String): Boolean { sent.add(text); return true }
        override fun send(bytes: okio.ByteString): Boolean = true
    }

    @Test
    fun messageBeforeSocketFieldAssignedIsNotDropped() {
        val client = LiveClient(OkHttpClient())
        val events = mutableListOf<LiveEvent>()
        val closedCalled = AtomicBoolean(false)
        val listener = object : LiveClient.Listener {
            override fun onEvent(event: LiveEvent) {
                events.add(event)
            }
            override fun onClosed(problem: String?) {
                closedCalled.set(true)
            }
        }

        val fakeWs = FakeWebSocket()
        val wsListener = client.createWebSocketListener(0, setup, listener)

        // Simulate onMessage firing before any socket assignment or returning
        wsListener.onMessage(fakeWs, """{"setupComplete": {}}""")

        assertEquals(1, events.size)
        assertTrue(events[0] is LiveEvent.SetupComplete)
    }

    @Test
    fun staleSessionCallbacksAreIgnored() {
        val client = LiveClient(OkHttpClient())
        val events = mutableListOf<LiveEvent>()
        val listener = object : LiveClient.Listener {
            override fun onEvent(event: LiveEvent) { events.add(event) }
            override fun onClosed(problem: String?) {}
        }
        val fakeWs = FakeWebSocket()
        val wsListener = client.createWebSocketListener(999, setup, listener)

        // Session ID 999 does not match current session (which is 0)
        wsListener.onMessage(fakeWs, """{"setupComplete": {}}""")
        assertTrue(events.isEmpty())
    }
}
