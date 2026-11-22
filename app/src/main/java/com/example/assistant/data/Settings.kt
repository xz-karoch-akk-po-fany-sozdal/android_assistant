package com.example.assistant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class CornerSide { LEFT, RIGHT, BOTH }

/** Plain preferences. Read lazily; nothing is cached in memory beyond the SharedPreferences object. */
class Settings(context: Context) {
    private val p: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = p.getBoolean("enabled", true)
        set(v) = p.edit().putBoolean("enabled", v).apply()

    var themeMode: ThemeMode
        get() = ThemeMode.valueOf(p.getString("theme", ThemeMode.SYSTEM.name)!!)
        set(v) = p.edit().putString("theme", v.name).apply()

    var cornerSide: CornerSide
        get() = CornerSide.valueOf(p.getString("side", CornerSide.BOTH.name)!!)
        set(v) = p.edit().putString("side", v.name).apply()

    /** Size of the swipe zone in dp. */
    var cornerSizeDp: Int
        get() = p.getInt("zone_dp", 72)
        set(v) = p.edit().putInt("zone_dp", v).apply()

    var backLongPress: Boolean
        get() = p.getBoolean("back_lp", true)
        set(v) = p.edit().putBoolean("back_lp", v).apply()

    var speak: Boolean
        get() = p.getBoolean("speak", true)
        set(v) = p.edit().putBoolean("speak", v).apply()

    /** Foreign language code paired with Russian for translation. */
    var foreignLang: String
        get() = p.getString("foreign", "en")!!
        set(v) = p.edit().putString("foreign", v).apply()

    var downloadOnlyWifi: Boolean
        get() = p.getBoolean("wifi_only", true)
        set(v) = p.edit().putBoolean("wifi_only", v).apply()

    var cloudModel: String
        get() = p.getString("cloud_model", "openai/gpt-4o-mini")!!
        set(v) = p.edit().putString("cloud_model", v).apply()

    var cloudBaseUrl: String
        get() = p.getString("cloud_url", "https://openrouter.ai/api/v1")!!
        set(v) = p.edit().putString("cloud_url", v).apply()
}

/** API key lives only in EncryptedSharedPreferences. Empty key = cloud mode off. */
class SecretStore(context: Context) {
    private val ctx = context.applicationContext

    private fun prefs(): SharedPreferences {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            ctx, "secrets", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getApiKey(): String = try { prefs().getString("api_key", "") ?: "" } catch (e: Exception) { "" }

    fun setApiKey(value: String) {
        try { prefs().edit().putString("api_key", value.trim()).apply() } catch (_: Exception) {}
    }
}

data class HistoryItem(val question: String, val answer: String, val time: Long)

/** Last answers, persisted as small JSON. */
class History(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun load(): List<HistoryItem> {
        val raw = p.getString("items", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            HistoryItem(o.getString("q"), o.getString("a"), o.getLong("t"))
        }
    }

    fun add(q: String, a: String) {
        val list = (listOf(HistoryItem(q, a, System.currentTimeMillis())) + load()).take(20)
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("q", it.question).put("a", it.answer).put("t", it.time))
        }
        p.edit().putString("items", arr.toString()).apply()
    }

    fun clear() = p.edit().clear().apply()
}
