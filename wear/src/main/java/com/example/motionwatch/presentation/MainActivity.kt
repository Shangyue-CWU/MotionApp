package com.example.motionwatch.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.motionwatch.presentation.theme.MotionWatchTheme
import com.google.android.gms.wearable.Wearable
import java.util.UUID

class MainActivity : ComponentActivity() {

    // ── Compose state ─────────────────────────────────────────────────────────

    private var gx by mutableStateOf(0f)
    private var gy by mutableStateOf(0f)
    private var gz by mutableStateOf(0f)

    /**
     * Current sync mode.
     * "Home" → standard Home-session recording (original behaviour).
     * "Live" → streams watch IMU to the Phone's Live Monitor section.
     * Can only be toggled when NOT logging.
     */
    private var mode by mutableStateOf("Home")

    private var activeSessionId: String? = null
    private var gyroReceiver: BroadcastReceiver? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        val filter = IntentFilter(SensorService.ACTION_GYRO_UPDATE)
        gyroReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != SensorService.ACTION_GYRO_UPDATE) return
                gx = intent.getFloatExtra("gx", 0f)
                gy = intent.getFloatExtra("gy", 0f)
                gz = intent.getFloatExtra("gz", 0f)
            }
        }
        registerReceiver(gyroReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        setContent {
            WearApp(
                mode         = mode,
                gx           = gx,
                gy           = gy,
                gz           = gz,
                onModeToggle = { mode = if (mode == "Home") "Live" else "Home" },
                startLogging = { sport -> startLoggingBoth(sport) },
                stopLogging  = { stopLoggingBoth() }
            )
        }

        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                Log.d(TAG_TEST, "Connected: ${nodes.map { it.displayName + ":" + it.id }}")
            }
            .addOnFailureListener { e -> Log.e(TAG_TEST, "connectedNodes failed", e) }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { gyroReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
    }

    // ── Session control ───────────────────────────────────────────────────────

    /**
     * Home mode → original behaviour: CSV recording on watch, /cmd/start on phone.
     * Live mode → streaming only on watch, /live/start on phone
     *             (phone's AnalyticsFragment responds by starting its session).
     */
    private fun startLoggingBoth(sport: String) {
        val sessionId = UUID.randomUUID().toString().replace("-", "").take(12)
        activeSessionId = sessionId

        val watchIntent = Intent(this, SensorService::class.java).apply {
            putExtra("sessionId",  sessionId)
            putExtra("sport",      if (mode == "Home") sport else "LIVE")
            putExtra("isLiveMode", mode == "Live")
        }
        startForegroundService(watchIntent)
        Log.d(TAG_CMD, "Started SensorService mode=$mode session=$sessionId")

        if (mode == "Home") {
            sendCommandToPhone("/cmd/start", "sessionId=$sessionId;label=$sport")
        } else {
            sendCommandToPhone("/live/start", "sessionId=$sessionId")
        }
    }

    private fun stopLoggingBoth() {
        val sessionId = activeSessionId ?: "unknown"
        stopService(Intent(this, SensorService::class.java))
        Log.d(TAG_CMD, "Stopped SensorService mode=$mode session=$sessionId")

        if (mode == "Home") {
            sendCommandToPhone("/cmd/stop",  "sessionId=$sessionId")
        } else {
            sendCommandToPhone("/live/stop", "sessionId=$sessionId")
        }
        activeSessionId = null
    }

    // ── Wearable Message API ──────────────────────────────────────────────────

    private fun sendCommandToPhone(path: String, payload: String) {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) { Log.e(TAG_CMD, "No nodes for $path"); return@addOnSuccessListener }
                val nodeId = nodes[0].id
                Wearable.getMessageClient(this)
                    .sendMessage(nodeId, path, payload.toByteArray(Charsets.UTF_8))
                    .addOnSuccessListener { Log.d(TAG_CMD, "Sent $path payload=$payload") }
                    .addOnFailureListener { e -> Log.e(TAG_CMD, "Failed $path", e) }
            }
            .addOnFailureListener { e -> Log.e(TAG_CMD, "connectedNodes failed for $path", e) }
    }

    companion object {
        private const val TAG_CMD  = "WearCmd"
        private const val TAG_TEST = "WearTest"
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
fun WearApp(
    mode:         String,
    gx:           Float,
    gy:           Float,
    gz:           Float,
    onModeToggle: () -> Unit,
    startLogging: (String) -> Unit,
    stopLogging:  () -> Unit
) {
    MotionWatchTheme {
        var isLogging     by remember { mutableStateOf(false) }
        var sportCategory by remember { mutableStateOf("JERKING") }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
        ) {
            TimeText(modifier = Modifier.align(Alignment.TopCenter))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Mode toggle ───────────────────────────────────────────────
                // Replaces old "Status: Not Logging / Logging…" text.
                // Blue = Home, Green = Live. Disabled while a session is active.
                ModeToggleButton(
                    mode     = mode,
                    enabled  = !isLogging,
                    onToggle = onModeToggle
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Gyro readout (unchanged)
                Text(
                    text      = "Gyro: %.2f, %.2f, %.2f".format(gx, gy, gz),
                    color     = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center,
                    style     = MaterialTheme.typography.body2
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ── Sport selector (Home mode only) ───────────────────────────
                // Hidden in Live mode — sport label is not needed for streaming.
                if (mode == "Home") {
                    Button(
                        onClick = {
                            if (!isLogging) {
                                sportCategory = when (sportCategory) {
                                    "JERKING"  -> "TONIC"
                                    "TONIC"    -> "FALLS"
                                    "FALLS"    -> "TREMOR"
                                    "TREMOR"   -> "RUNNING"
                                    "RUNNING"  -> "WALKING"
                                    "WALKING"  -> "SITTING"
                                    "SITTING"  -> "STANDING"
                                    "STANDING" -> "JERKING"
                                    else       -> "JERKING"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 1.dp)
                            .height(24.dp)
                    ) {
                        Text(text = sportCategory, style = MaterialTheme.typography.title3)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // ── Start / Stop (unchanged) ──────────────────────────────────
                Button(
                    onClick = {
                        if (!isLogging) { startLogging(sportCategory); isLogging = true  }
                        else            { stopLogging();                isLogging = false }
                    }
                ) {
                    Text(if (!isLogging) "Start" else "Stop")
                }
            }
        }
    }
}

/**
 * Pill button displaying the current mode.
 * Blue  (#2A3FBF) = Home mode.
 * Green (#2D6A5A) = Live mode.
 * Disabled (semi-transparent) while logging — prevents mid-session mode changes.
 */
@Composable
fun ModeToggleButton(mode: String, enabled: Boolean, onToggle: () -> Unit) {
    val isLive   = mode == "Live"
    val bgColor  = if (isLive) Color(0xFF2D6A5A) else Color(0xFF2A3FBF)
    val alpha    = if (enabled) 1f else 0.45f

    Button(
        onClick  = { if (enabled) onToggle() },
        enabled  = enabled,
        colors   = ButtonDefaults.buttonColors(
            backgroundColor = bgColor.copy(alpha = alpha)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .height(28.dp)
    ) {
        Text(
            text       = mode,
            color      = Color.White.copy(alpha = alpha),
            fontWeight = FontWeight.SemiBold,
            style      = MaterialTheme.typography.title3
        )
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp(
        mode         = "Home",
        gx           = 0f, gy = 0f, gz = 0f,
        onModeToggle = {},
        startLogging = {},
        stopLogging  = {}
    )
}