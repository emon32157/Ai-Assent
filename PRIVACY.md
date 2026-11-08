# Privacy

JARVIS has **no server and no account**. The developer receives nothing from your phone: no
analytics, no crash reports, no usage data.

## What stays on your phone

- Your Gemini API key (encrypted with Android Keystore).
- Notes you ask it to remember, routines, settings and learned habits (app-private storage;
  never backed up to the cloud or moved to another device).
- Notifications it reads for "what did I miss?" and "watch my messages": **in memory only**,
  at most the last 12 hours, gone when the app stops.
- A diagnostic log on the phone with timings and states, **never your words**.
- An activity log of actions taken (e.g. "call confirmed, placed", "call refused", "dialer opened"), **never your words or phone numbers**, cleared when the app closes.

## What goes to Google (Gemini), and when

Only while you're talking to JARVIS, with **your own key**:

- What you say or type, and JARVIS's answers.
- Results of the tools it uses for your request: for example the summary of your
  notifications (codes hidden), your calendar for the day you asked about, a note you asked
  it to recall, or the text of the screen when you ask it to use an app.
- A screenshot, only for "tap by sight" when an app's buttons have no text (never from
  banking, payment or password screens, which Android blocks anyway).

Google's handling of that data follows its Gemini API terms for your key (on the free tier
Google may use it to improve its products; with billing turned on it says it doesn't).

## Other services

- **Open-Meteo** gets the city name when you ask about the weather.
- Apps JARVIS opens for you (WhatsApp, Calendar, Gmail…) follow their own privacy policies.

## Permissions

Every permission is asked only when a feature first needs it, and each can be refused:
microphone (talking), contacts (calls and messages), phone calls (hands-free calling after confirmation), calendar, notifications access, display
over other apps (the floating orb), Accessibility (phone control). You can turn any of them
off in Android Settings at any time; uninstalling JARVIS deletes everything it kept.
