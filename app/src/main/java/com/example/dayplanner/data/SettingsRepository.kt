package com.example.dayplanner.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.settingsStore by preferencesDataStore(name = "settings")

fun Settings.toJson(): JSONObject = JSONObject()
    .put("showLeftArrow", showLeftArrow)
    .put("showLock", showLock)
    .put("dateFormat", dateFormat)
    .put("rows", rows)
    .put("columns", columns)
    .put("dayStart", dayStart)
    .put("priorities", priorities)
    .put("priorityHistory", JSONObject(priorityHistory as Map<*, *>))
    .put("showPrioritiesTitle", showPrioritiesTitle)
    .put("showBraindumpTitle", showBraindumpTitle)
    .put("showTimeboxTitle", showTimeboxTitle)
    .put("wrapText", wrapText)
    .put("locked", locked)
    .put("design", design)
    .put("themeMode", themeMode)
    .put("zoom", zoom)
    .put("drawing", drawing)

fun settingsFromJson(o: JSONObject): Settings {
    val d = Settings()
    val history = o.optJSONObject("priorityHistory")?.let { h ->
        h.keys().asSequence().associateWith { h.optInt(it, d.priorities) }
    } ?: emptyMap()
    return Settings(
        showLeftArrow = o.optBoolean("showLeftArrow", d.showLeftArrow),
        showLock = o.optBoolean("showLock", d.showLock),
        dateFormat = o.optInt("dateFormat", d.dateFormat),
        rows = o.optInt("rows", d.rows),
        columns = o.optInt("columns", d.columns),
        dayStart = o.optInt("dayStart", d.dayStart),
        priorities = o.optInt("priorities", d.priorities),
        priorityHistory = history,
        showPrioritiesTitle = o.optBoolean("showPrioritiesTitle", d.showPrioritiesTitle),
        showBraindumpTitle = o.optBoolean("showBraindumpTitle", d.showBraindumpTitle),
        showTimeboxTitle = o.optBoolean("showTimeboxTitle", d.showTimeboxTitle),
        wrapText = o.optBoolean("wrapText", d.wrapText),
        locked = o.optBoolean("locked", d.locked),
        design = o.optInt("design", d.design),
        themeMode = o.optInt("themeMode", d.themeMode),
        zoom = o.optBoolean("zoom", d.zoom),
        drawing = o.optBoolean("drawing", d.drawing),
    ).sanitized()
}

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    private val kSettings = stringPreferencesKey("settings_json")
    private val kPwType = intPreferencesKey("pw_type")
    private val kPwSalt = stringPreferencesKey("pw_salt")
    private val kPwHash = stringPreferencesKey("pw_hash")

    private fun Preferences.toSettings(): Settings =
        this[kSettings]?.let { runCatching { settingsFromJson(JSONObject(it)) }.getOrNull() }
            ?: Settings()

    val flow: Flow<Settings> = store.data.map { it.toSettings() }

    val passwordFlow: Flow<PasswordInfo> = store.data.map { p ->
        PasswordInfo(p[kPwType] ?: PasswordInfo.NONE, p[kPwSalt] ?: "", p[kPwHash] ?: "")
    }

    suspend fun current(): Settings = flow.first()

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p -> p[kSettings] = transform(p.toSettings()).sanitized().toJson().toString() }
    }

    // ---------- Passwort (nur Hash, kein Klartext) ----------

    suspend fun setPassword(type: Int, secret: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = withContext(Dispatchers.Default) { pbkdf2(secret, salt) }
        store.edit { p ->
            p[kPwType] = type
            p[kPwSalt] = Base64.encodeToString(salt, Base64.NO_WRAP)
            p[kPwHash] = Base64.encodeToString(hash, Base64.NO_WRAP)
        }
    }

    suspend fun clearPassword() {
        store.edit { p ->
            p.remove(kPwType)
            p.remove(kPwSalt)
            p.remove(kPwHash)
        }
    }

    suspend fun check(info: PasswordInfo, input: String): Boolean = withContext(Dispatchers.Default) {
        if (info.type == PasswordInfo.NONE) return@withContext true
        val salt = Base64.decode(info.salt, Base64.NO_WRAP)
        val expected = Base64.decode(info.hash, Base64.NO_WRAP)
        MessageDigest.isEqual(expected, pbkdf2(input, salt))
    }

    private fun pbkdf2(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, 60_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
