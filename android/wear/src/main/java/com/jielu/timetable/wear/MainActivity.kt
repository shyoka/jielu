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
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        SettingsStore.init(this)
        ScheduleStore.ensureSample(this)
        setContent {
            WearApp()
        }
    }
}
