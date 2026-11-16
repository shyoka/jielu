package com.jielu.timetable.wear.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 与手机端深色色板同源的四套配色。 */
data class WearPalette(
    val key: String,
    val label: String,
    val accent: Long,
    val accentDeep: Long,
    val secondary: Long,
    val onAccent: Long
)

object WearPalettes {
    val clay = WearPalette("clay", "陶土", 0xFFFFB4A4, 0xFF703528, 0xFFE4BEB4, 0xFF2B1410)
    val moss = WearPalette("moss", "苔绿", 0xFFA6D2B8, 0xFF264F3C, 0xFFB8CDC0, 0xFF10301F)
    val iris = WearPalette("iris", "鸢尾", 0xFFCFBEF9, 0xFF453463, 0xFFCDC1D9, 0xFF2A1C42)
    val tide = WearPalette("tide", "潮汐", 0xFFA1CEE5, 0xFF234C5F, 0xFFB8C9D3, 0xFF0E2D3A)

    val all = listOf(clay, moss, iris, tide)

    fun byKey(key: String?): WearPalette = all.firstOrNull { it.key == key } ?: clay
}

/** 手表端可调的几项：配色、今日是否折叠已结束的课、首页是否画进度环、调试模式。 */
data class WearSettings(
    val palette: WearPalette = WearPalettes.clay,
    val hideFinished: Boolean = false,
    val showRing: Boolean = true,
    val debug: Boolean = false
)

object SettingsStore {
    private const val PREFS = "jielu.wear.settings"
    private const val KEY_PALETTE = "palette"
    private const val KEY_HIDE_FINISHED = "hideFinished"
    private const val KEY_SHOW_RING = "showRing"
    private const val KEY_DEBUG = "debug"

    private var appContext: Context? = null

    private val _settings = MutableStateFlow(WearSettings())
    val settings: StateFlow<WearSettings> = _settings

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _settings.value = WearSettings(
            palette = WearPalettes.byKey(prefs.getString(KEY_PALETTE, null)),
            hideFinished = prefs.getBoolean(KEY_HIDE_FINISHED, false),
            showRing = prefs.getBoolean(KEY_SHOW_RING, true),
            debug = prefs.getBoolean(KEY_DEBUG, false)
        )
    }

    fun setPalette(key: String) {
        update { it.copy(palette = WearPalettes.byKey(key)) }
        persist { putString(KEY_PALETTE, key) }
    }

    fun setHideFinished(value: Boolean) {
        update { it.copy(hideFinished = value) }
        persist { putBoolean(KEY_HIDE_FINISHED, value) }
    }

    fun setShowRing(value: Boolean) {
        update { it.copy(showRing = value) }
        persist { putBoolean(KEY_SHOW_RING, value) }
    }

    fun setDebug(value: Boolean) {
        update { it.copy(debug = value) }
        persist { putBoolean(KEY_DEBUG, value) }
    }

    private fun update(block: (WearSettings) -> WearSettings) {
        _settings.value = block(_settings.value)
    }

    private fun persist(block: android.content.SharedPreferences.Editor.() -> Unit) {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply(block).apply()
    }
}
