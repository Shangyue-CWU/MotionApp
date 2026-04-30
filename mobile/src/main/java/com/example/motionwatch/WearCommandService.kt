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

            PATH_START -> {
                val map          = parsePayload(payload)
                val label        = map["label"] ?: "UNKNOWN"
                val sessionId    = map["sessionId"] ?: "unknown"
                val startEpochMs = System.currentTimeMillis()

                Log.d(TAG, "START cmd label=$label session=$sessionId")

                // 1) Start phone logging service
                val svc = Intent(this, SensorLoggerService::class.java).apply {
                    action = ACTION_START
                    putExtra("label",        label)
                    putExtra("sessionId",    sessionId)
                    putExtra("startEpochMs", startEpochMs)
                }
                startForegroundService(svc)

                // 2) Broadcast UI command so CollectFragment updates like Start was tapped
                val ui = Intent(ACTION_UI_CMD).apply {
                    putExtra("cmd",          "start")
                    putExtra("label",        label)
                    putExtra("sessionId",    sessionId)
                    putExtra("startEpochMs", startEpochMs)
                }
                sendBroadcast(ui)

                Log.d(TAG, "UI_CMD start broadcast sent")
            }

            PATH_STOP -> {
                Log.d(TAG, "STOP cmd received")

                // 1) Stop phone logging service
                val svc = Intent(this, SensorLoggerService::class.java).apply {
                    action = ACTION_STOP
                }
                startService(svc)

                // 2) Broadcast UI command so CollectFragment updates like Stop was tapped
                val ui = Intent(ACTION_UI_CMD).apply {
                    putExtra("cmd", "stop")
                }
                sendBroadcast(ui)

                Log.d(TAG, "UI_CMD stop broadcast sent")
            }

            // ── Watch sensor data streaming ──────────────────────────────────
            // The watch app sends real-time IMU readings on this path so
            // AnalyticsFragment (Live section) can display and classify them.
            //
            // Payload format (semicolon-separated key=value pairs):
            //   "ax=0.123;ay=-0.456;az=9.810;gx=0.001;gy=-0.002;gz=0.003"
            //
            // The watch-side app must publish a WearOS MessageClient message to
            // PATH_SENSOR_DATA at the desired streaming rate (target: 50 Hz).
            PATH_SENSOR_DATA -> {
                val map = parsePayload(payload)
                val ax  = map[EXTRA_AX]?.toFloatOrNull() ?: 0f
                val ay  = map[EXTRA_AY]?.toFloatOrNull() ?: 0f
                val az  = map[EXTRA_AZ]?.toFloatOrNull() ?: 0f
                val gx  = map[EXTRA_GX]?.toFloatOrNull() ?: 0f
                val gy  = map[EXTRA_GY]?.toFloatOrNull() ?: 0f
                val gz  = map[EXTRA_GZ]?.toFloatOrNull() ?: 0f

                // Forward to AnalyticsFragment via global broadcast.
                // setPackage() ensures the intent stays within this app.
                val broadcast = Intent(ACTION_SENSOR_DATA).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_AX, ax)
                    putExtra(EXTRA_AY, ay)
                    putExtra(EXTRA_AZ, az)
                    putExtra(EXTRA_GX, gx)
                    putExtra(EXTRA_GY, gy)
                    putExtra(EXTRA_GZ, gz)
                }
                sendBroadcast(broadcast)

                Log.v(TAG, "SENSOR_DATA forwarded ax=$ax ay=$ay az=$az gx=$gx gy=$gy gz=$gz")
            }

            else -> Log.w(TAG, "Unknown path: $path")
        }
    }

    /**
     * Payload format: "sessionId=abc123;label=RUNNING"
     * Also used for sensor data: "ax=0.1;ay=0.2;az=9.8;gx=0.0;gy=0.0;gz=0.0"
     */
    private fun parsePayload(payload: String): Map<String, String> {
        if (payload.isBlank()) return emptyMap()
        return payload.split(";")
            .mapNotNull { part ->
                val idx = part.indexOf("=")
                if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
            }
            .toMap()
    }

    companion object {
        private const val TAG = "WearCommand"

        // ── Service actions ──────────────────────────────────────────────────
        const val ACTION_START = "com.example.motionwatch.action.START"
        const val ACTION_STOP  = "com.example.motionwatch.action.STOP"

        // ── Wearable message paths (watch → phone) ───────────────────────────
        const val PATH_START       = "/cmd/start"
        const val PATH_STOP        = "/cmd/stop"
        // New: watch streams real-time IMU data for the Live section
        const val PATH_SENSOR_DATA = "/data/sensor"

        // ── Broadcast actions ────────────────────────────────────────────────
        // CollectFragment listens to this for watch-initiated start/stop
        const val ACTION_UI_CMD = "com.example.motionwatch.UI_CMD"
        // AnalyticsFragment listens to this for live watch IMU data
        const val ACTION_SENSOR_DATA = "com.example.motionwatch.WEAR_SENSOR_DATA"

        // ── Extra keys for ACTION_SENSOR_DATA ────────────────────────────────
        // Names deliberately match the extras used by SensorLoggerService
        // ACTION_LIVE_SAMPLE so AnalyticsFragment can handle both the same way.
        const val EXTRA_AX = "ax"
        const val EXTRA_AY = "ay"
        const val EXTRA_AZ = "az"
        const val EXTRA_GX = "gx"
        const val EXTRA_GY = "gy"
        const val EXTRA_GZ = "gz"
    }
}