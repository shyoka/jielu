package com.jielu.timetable.wear.data

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * 手机端通过 Data Layer 推送 /jielu/data，这里落到本地存储。
 * 手机端发送的是整份 jielu.data.v2 JSON，格式与网页端导出完全一致。
 */
class SyncService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.use { buffer ->
            for (event in buffer) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val path = event.dataItem.uri.path ?: continue
                if (!path.startsWith("/jielu/data")) continue
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val raw = map.getString("json") ?: continue
                ScheduleStore.init(applicationContext)
                val ok = ScheduleStore.import(raw, "phone")
                Log.i(TAG, if (ok) "收到手机课表 ${raw.length} 字节" else "课表解析失败")
            }
        }
    }

    private companion object {
        const val TAG = "JieluWearSync"
    }
}
