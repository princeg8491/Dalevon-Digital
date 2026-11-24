package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.orbitThemeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "orbit_theme_preferences"
)

enum class DarkThemeMode(val id: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromId(id: String?): DarkThemeMode =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

enum class OrbitFontPreset(
    val id: String,
    val label: String,
    val styleSubtitle: String
) {
    ORBIT_DEFAULT("orbit_default", "Orbit Default", "Plus Jakarta Sans · Modern geometric"),
    THREADS("threads", "Threads", "Inter · Crisp editorial neo-grotesque"),
    TWITTER("twitter", "Twitter / X", "DM Sans · Clean high-velocity sans"),
    INSTAGRAM("instagram", "Instagram", "Outfit · Expressive modern display"),
    FACEBOOK("facebook", "Facebook", "Open Sans · Friendly humanist clarity");

    companion object {
        fun fromId(id: String?): OrbitFontPreset =
            entries.firstOrNull { it.id == id } ?: ORBIT_DEFAULT
    }
}

enum class OrbitNavSlot(val id: String, val label: String) {
    HOME("home", "Home"),
    SEARCH("search", "Search"),
    COMPOSE("compose", "Compose"),
    SPACES("spaces", "Spaces"),
    DMS("dms", "DMs"),
    ACTIVITY("activity", "Activity"),
    PROFILE("profile", "Profile");

    companion object {
        val DEFAULT_ENABLED: List<OrbitNavSlot> = listOf(HOME, SEARCH, COMPOSE, ACTIVITY, PROFILE)
        const val MAX_SLOTS: Int = 6
        const val MIN_SLOTS: Int = 2

        fun fromId(id: String): OrbitNavSlot? = entries.firstOrNull { it.id == id }

        fun parseSerialized(raw: String?): List<OrbitNavSlot> {
            if (raw.isNullOrBlank()) return DEFAULT_ENABLED
            val parsed = raw.split(",")
                .mapNotNull { fromId(it.trim()) }
                .distinct()
                .take(MAX_SLOTS)
            return if (parsed.size >= MIN_SLOTS) parsed else DEFAULT_ENABLED
        }

        fun serialize(slots: List<OrbitNavSlot>): String =
            slots.distinct().take(MAX_SLOTS).joinToString(",") { it.id }
    }
}

enum class OrbitDockTab(val id: String, val label: String) {
    SAVED_POSTS("saved_posts", "Saved Posts"),
    THEME_STUDIO("theme_studio", "Theme Studio"),
    SPACES("spaces", "Spaces"),
    DMS("dms", "Messages"),
    PROFILE("profile", "Profile");

    companion object {
        val DEFAULT_ORDER: List<OrbitDockTab> = listOf(SAVED_POSTS, THEME_STUDIO, SPACES, DMS, PROFILE)

        fun fromId(id: String): OrbitDockTab? = entries.firstOrNull { it.id == id }

        fun parseSerialized(raw: String?): List<OrbitDockTab> {
            if (raw.isNullOrBlank()) return DEFAULT_ORDER
            val parsed = raw.split(",")
                .mapNotNull { fromId(it.trim()) }
                .distinct()
            val missing = DEFAULT_ORDER.filter { it !in parsed }
            return (parsed + missing).ifEmpty { DEFAULT_ORDER }
        }

        fun serialize(tabs: List<OrbitDockTab>): String {
            val complete = (tabs.distinct() + DEFAULT_ORDER).distinct()
            return complete.joinToString(",") { it.id }
        }
    }
}

data class OrbitThemeConfig(
    val dynamicWallpaperEnabled: Boolean = true,
    val darkThemeMode: DarkThemeMode = DarkThemeMode.SYSTEM,
    val customSeedColorArgb: Long? = null,
    val fontPreset: OrbitFontPreset = OrbitFontPreset.ORBIT_DEFAULT,
    val fontScale: Float = 1.0f,
    val hapticsEnabled: Boolean = true,
    val hapticsStrength: Float = 0.65f,
    val readReceiptsEnabled: Boolean = true,
    val bottomNavSlots: List<OrbitNavSlot> = OrbitNavSlot.DEFAULT_ENABLED,
    val dockTabOrder: List<OrbitDockTab> = OrbitDockTab.DEFAULT_ORDER,
    val lastSeenActivityMillis: Long = 0L,
    val lastSeenMessagesMillis: Long = 0L
) {
    fun resolveDarkTheme(systemInDarkTheme: Boolean): Boolean = when (darkThemeMode) {
        DarkThemeMode.SYSTEM -> systemInDarkTheme
        DarkThemeMode.LIGHT -> false
        DarkThemeMode.DARK -> true
    }
}

class ThemePreferencesRepository(private val context: Context) {

    private object Keys {
        val DYNAMIC_WALLPAPER = booleanPreferencesKey("dynamic_wallpaper_enabled")
        val DARK_THEME_MODE = stringPreferencesKey("dark_theme_mode")
        val CUSTOM_SEED_COLOR = longPreferencesKey("custom_seed_color_argb")
        val HAS_CUSTOM_COLOR = booleanPreferencesKey("has_custom_seed_color")
        val FONT_PRESET = stringPreferencesKey("font_preset")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val HAPTICS_STRENGTH = floatPreferencesKey("haptics_strength")
        val READ_RECEIPTS_ENABLED = booleanPreferencesKey("read_receipts_enabled")
        val BOTTOM_NAV_SLOTS = stringPreferencesKey("bottom_nav_slots")
        val DOCK_TAB_ORDER = stringPreferencesKey("dock_tab_order")
        val LAST_SEEN_ACTIVITY_MILLIS = longPreferencesKey("last_seen_activity_millis")
        val LAST_SEEN_MESSAGES_MILLIS = longPreferencesKey("last_seen_messages_millis")
    }

    val themeConfigFlow: Flow<OrbitThemeConfig> = context.orbitThemeDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val hasCustom = prefs[Keys.HAS_CUSTOM_COLOR] ?: false
            OrbitThemeConfig(
                dynamicWallpaperEnabled = prefs[Keys.DYNAMIC_WALLPAPER] ?: true,
                darkThemeMode = DarkThemeMode.fromId(prefs[Keys.DARK_THEME_MODE]),
                customSeedColorArgb = if (hasCustom) prefs[Keys.CUSTOM_SEED_COLOR] else null,
                fontPreset = OrbitFontPreset.fromId(prefs[Keys.FONT_PRESET]),
                fontScale = (prefs[Keys.FONT_SCALE] ?: 1.0f).coerceIn(0.85f, 1.35f),
                hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
                hapticsStrength = (prefs[Keys.HAPTICS_STRENGTH] ?: 0.65f).coerceIn(0.1f, 1.0f),
                readReceiptsEnabled = prefs[Keys.READ_RECEIPTS_ENABLED] ?: true,
                bottomNavSlots = OrbitNavSlot.parseSerialized(prefs[Keys.BOTTOM_NAV_SLOTS]),
                dockTabOrder = OrbitDockTab.parseSerialized(prefs[Keys.DOCK_TAB_ORDER]),
                lastSeenActivityMillis = prefs[Keys.LAST_SEEN_ACTIVITY_MILLIS] ?: 0L,
                lastSeenMessagesMillis = prefs[Keys.LAST_SEEN_MESSAGES_MILLIS] ?: 0L
            )
        }

    suspend fun setDynamicWallpaperEnabled(enabled: Boolean) {
        context.orbitThemeDataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_WALLPAPER] = enabled
        }
    }

    suspend fun setDarkThemeMode(mode: DarkThemeMode) {
        context.orbitThemeDataStore.edit { prefs ->
            prefs[Keys.DARK_THEME_MODE] = mode.id
        }
    }

    suspend fun setManualThemeOverride(
        overrideSystemMaterialYou: Boolean,
        mode: DarkThemeMode
    ) {
        context.orbitThemeDataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_WALLPAPER] = !overrideSystemMaterialYou
            prefs[Keys.DARK_THEME_MODE] = if (overrideSystemMaterialYou) {
                mode.id
            } else {
                DarkThemeMode.SYSTEM.id
            }
        }
    }

    suspend fun setCustomSeedColorArgb(argb: Long?) {
        context.orbitThemeDataStore.edit { prefs ->
            if (argb == null) {
                prefs[Keys.HAS_CUSTOM_COLOR] = false
                prefs.remove(Keys.CUSTOM_SEED_COLOR)
            } else {
                prefs[Keys.HAS_CUSTOM_COLOR] = true
                prefs[Keys.CUSTOM_SEED_COLOR] = argb
                prefs[Keys.DYNAMIC_WALLPAPER] = false
            }
        }
    }

    suspend fun setFontPreset(preset: OrbitFontPreset) {
        context.orbitThemeDataStore.edit { prefs ->
            prefs[Keys.FONT_PRESET] = preset.id
        }
    }

    suspend fun setFontScale(scale: Float) {
        context.orbitThemeDataStore.edit { prefs ->
            prefs[Keys.FONT_SCALE] = scale.coerceIn(0.85f, 1.35f)
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.orbitThemeDataStore.edit { prefs ->
            prefs[Keys.HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun setHapticsStrength(strength: Float) {
        context.orbitThemeDataStore.edit { prefs ->
            prefs[Keys.HAPTICS_STRENGTH] = strength.coerceIn(0.1f, 1.0f)
        }
    }

    suspend fun setReadReceiptsEnabled(enabled: Boolean) {
        context.orbitThemeDataStore.edit { prefs ->
            prefs[Keys.READ_RECEIPTS_ENABLED] = enabled
        }
    }

    suspend fun setBottomNavSlots(slots: List<OrbitNavSlot>) {
        val sanitized = slots.distinct().take(OrbitNavSlot.MAX_SLOTS)
        if (sanitized.size < OrbitNavSlot.MIN_SLOTS) return
        context.orbitThemeDataStore.edit { prefs ->
            prefs[Keys.BOTTOM_NAV_SLOTS] = OrbitNavSlot.serialize(sanitized)
        }
    }

    suspend fun setDockTabOrder(tabs: List<OrbitDockTab>) {
        context.orbitThemeDataStore.edit { prefs ->
            prefs[Keys.DOCK_TAB_ORDER] = OrbitDockTab.serialize(tabs)
        }
    }

    suspend fun setLastSeenActivityMillis(millis: Long) {
        context.orbitThemeDataStore.edit { prefs ->
            val current = prefs[Keys.LAST_SEEN_ACTIVITY_MILLIS] ?: 0L
            if (millis > current) {
                prefs[Keys.LAST_SEEN_ACTIVITY_MILLIS] = millis
            }
        }
    }

    suspend fun setLastSeenMessagesMillis(millis: Long) {
        context.orbitThemeDataStore.edit { prefs ->
            val current = prefs[Keys.LAST_SEEN_MESSAGES_MILLIS] ?: 0L
            if (millis > current) {
                prefs[Keys.LAST_SEEN_MESSAGES_MILLIS] = millis
            }
        }
    }
}
