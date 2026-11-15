package com.jielu.timetable.wear

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jielu.timetable.wear.data.ScheduleStore
import com.jielu.timetable.wear.data.SettingsStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 兜底：部分完整 Android 手表 / 国产 ROM 会在主题之外再补一个标题栏，
        // 这里主动隐藏，避免顶部一直挂着应用名。
        actionBar?.hide()
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        SettingsStore.init(this)
        // 只挂上 context，真正的读盘与 JSON 解析在 IO 线程做，
        // 首帧不必等课表解析完再画
        ScheduleStore.init(this)
        setContent {
            WearApp()
        }
    }
}
