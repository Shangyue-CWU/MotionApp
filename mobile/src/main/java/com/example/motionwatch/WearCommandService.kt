package com.example.motionwatch

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearCommandService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val path    = event.path
        val payload = event.data?.toString(Charsets.UTF_8) ?: ""
        Log.d(TAG, "onMessageReceived path=$path payload=$payload")

        when (path) {

            // ── Home mode: start phone CSV recording ─────────────────────────
            PATH_START -> {
                val map          = parsePayload(payload)
                val label        = map["label"]     ?: "UNKNOWN"
                val sessionId    = map["sessionId"] ?: "unknown"
                val startEpochMs = System.currentTimeMillis()
                Log.d(TAG, "HOME START label=$label session=$sessionId")

                startForegroundService(
                    Intent(this, SensorLoggerService::class.java).apply {
                        action = ACTION_START
                        putExtra("label",        label)
                        putExtra("sessionId",    sessionId)
                        putExtra("startEpochMs", startEpochMs)
                    }
                )
                sendBroadcast(Intent(ACTION_UI_CMD).apply {
                    putExtra("cmd",          "start")
                    putExtra("label",        label)
                    putExtra("sessionId",    sessionId)
                    putExtra("startEpochMs", startEpochMs)
                })
            }

            // ── Home mode: stop phone CSV recording ──────────────────────────
            PATH_STOP -> {
                Log.d(TAG, "HOME STOP")
                startService(Intent(this, SensorLoggerService::class.java).apply {
                    action = ACTION_STOP
                })
                sendBroadcast(Intent(ACTION_UI_CMD).apply { putExtra("cmd", "stop") })
            }

            // ── Live mode: start phone Live Monitor ──────────────────────────
            // AnalyticsFragment receives ACTION_LIVE_CMD and calls startRecording()
            // exactly as if the user had tapped "START SESSION" themselves.
            PATH_LIVE_START -> {
                val sessionId = parsePayload(payload)["sessionId"] ?: "unknown"
                Log.d(TAG, "LIVE START session=$sessionId")
                sendBroadcast(Intent(ACTION_LIVE_CMD).apply {
                    setPackage(packageName)
                    putExtra("cmd",       "start")
                    putExtra("sessionId", sessionId)
                })
            }

            // ── Live mode: stop phone Live Monitor ───────────────────────────
            PATH_LIVE_STOP -> {
                val sessionId = parsePayload(payload)["sessionId"] ?: "unknown"
                Log.d(TAG, "LIVE STOP session=$sessionId")
                sendBroadcast(Intent(ACTION_LIVE_CMD).apply {
                    setPackage(packageName)
                    putExtra("cmd",       "stop")
                    putExtra("sessionId", sessionId)
                })
            }

            // ── Live mode: real-time IMU stream from watch ───────────────────
            // SensorService (watch) sends this at 10 Hz.
            // Forwarded to AnalyticsFragment via ACTION_SENSOR_DATA.
            // AnalyticsFragment labels these as WACC / WGYRO.
            PATH_SENSOR_DATA -> {
                val map = parsePayload(payload)
                val ax  = map[EXTRA_AX]?.toFloatOrNull() ?: 0f
                val ay  = map[EXTRA_AY]?.toFloatOrNull() ?: 0f
                val az  = map[EXTRA_AZ]?.toFloatOrNull() ?: 0f
                val gx  = map[EXTRA_GX]?.toFloatOrNull() ?: 0f
                val gy  = map[EXTRA_GY]?.toFloatOrNull() ?: 0f
                val gz  = map[EXTRA_GZ]?.toFloatOrNull() ?: 0f

                sendBroadcast(Intent(ACTION_SENSOR_DATA).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_AX, ax); putExtra(EXTRA_AY, ay); putExtra(EXTRA_AZ, az)
                    putExtra(EXTRA_GX, gx); putExtra(EXTRA_GY, gy); putExtra(EXTRA_GZ, gz)
                })
                Log.v(TAG, "SENSOR_DATA forwarded ax=$ax ay=$ay az=$az")
            }

            else -> Log.w(TAG, "Unknown path: $path")
        }
    }

    private fun parsePayload(payload: String): Map<String, String> {
        if (payload.isBlank()) return emptyMap()
        return payload.split(";").mapNotNull { part ->
            val idx = part.indexOf("=")
            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()
    }

    companion object {
        private const val TAG = "WearCommand"

        const val ACTION_START = "com.example.motionwatch.action.START"
        const val ACTION_STOP  = "com.example.motionwatch.action.STOP"

        // Wearable message paths
        const val PATH_START       = "/cmd/start"
        const val PATH_STOP        = "/cmd/stop"
        const val PATH_LIVE_START  = "/live/start"
        const val PATH_LIVE_STOP   = "/live/stop"
        const val PATH_SENSOR_DATA = "/data/sensor"

        // Broadcast actions
        const val ACTION_UI_CMD      = "com.example.motionwatch.UI_CMD"
        const val ACTION_LIVE_CMD    = "com.example.motionwatch.LIVE_CMD"
        const val ACTION_SENSOR_DATA = "com.example.motionwatch.WEAR_SENSOR_DATA"

        // Extra keys
        const val EXTRA_AX = "ax"; const val EXTRA_AY = "ay"; const val EXTRA_AZ = "az"
        const val EXTRA_GX = "gx"; const val EXTRA_GY = "gy"; const val EXTRA_GZ = "gz"
    }
}