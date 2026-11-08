package app.shilex.android.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {
    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = map[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class Editor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) removed.add(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clear = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clear) prefs.map.clear()
                for (r in removed) prefs.map.remove(r)
                prefs.map.putAll(pending)
            }
        }
    }

    @Test
    fun migratesHindiAndHinglishToAuto() {
        val prefs = FakeSharedPreferences()
        val settings = Settings(prefs = prefs)

        prefs.edit().putString("reply_language", "hindi").apply()
        assertEquals("auto", settings.replyLanguage)

        prefs.edit().putString("reply_language", "hinglish").apply()
        assertEquals("auto", settings.replyLanguage)

        prefs.edit().putString("reply_language", "english").apply()
        assertEquals("english", settings.replyLanguage)

        prefs.edit().putString("reply_language", "bengali").apply()
        assertEquals("bengali", settings.replyLanguage)

        prefs.edit().putString("reply_language", "auto").apply()
        assertEquals("auto", settings.replyLanguage)
    }

    @Test
    fun replyLanguagesListOnlyContainsAutoEnglishBengali() {
        assertEquals(listOf("auto", "english", "bengali"), Settings.REPLY_LANGUAGES)
    }
}
