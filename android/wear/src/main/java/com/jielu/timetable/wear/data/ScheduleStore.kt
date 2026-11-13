package com.jielu.timetable.wear.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader

/**
 * 手表端的唯一数据源。手机通过 Data Layer 发来的 JSON 直接写进
 * SharedPreferences，UI 订阅 revision 重新读取，避免跨进程通信。
 */
object ScheduleStore {
    private const val PREFS = "jielu.wear"
    private const val KEY_JSON = "schedule.json"
    private const val KEY_SYNCED_AT = "synced.at"
    private const val KEY_SOURCE = "synced.source"

    private var appContext: Context? = null

    private val _data = MutableStateFlow<ScheduleData?>(null)
    val data: StateFlow<ScheduleData?> = _data

    private val _syncedAt = MutableStateFlow(0L)
    val syncedAt: StateFlow<Long> = _syncedAt

    private val _source = MutableStateFlow("")
    val source: StateFlow<String> = _source

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            reload()
        }
    }

    fun reload() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_JSON, null)
        _data.value = raw?.let { ScheduleData.parse(it) }
        _syncedAt.value = prefs.getLong(KEY_SYNCED_AT, 0L)
        _source.value = prefs.getString(KEY_SOURCE, "") ?: ""
    }

    /** 返回是否解析成功；失败时不覆盖旧数据。 */
    fun import(raw: String, source: String): Boolean {
        val ctx = appContext ?: return false
        val parsed = ScheduleData.parse(raw) ?: return false
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_JSON, raw)
            .putLong(KEY_SYNCED_AT, System.currentTimeMillis())
            .putString(KEY_SOURCE, source)
            .apply()
        _data.value = parsed
        _syncedAt.value = System.currentTimeMillis()
        _source.value = source
        return true
    }

    fun clear() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        _data.value = null
        _syncedAt.value = 0L
        _source.value = ""
    }

    /** 首次打开时给一份示例，避免对着空白表盘。 */
    fun ensureSample(context: Context) {
        init(context)
        if (_data.value != null) return
        runCatching {
            val raw = context.assets.open("sample.json").bufferedReader().use(BufferedReader::readText)
            import(raw, "sample")
        }
    }
}
