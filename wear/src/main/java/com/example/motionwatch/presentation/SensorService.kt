package com.example.motionwatch.presentation

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.motionwatch.R
import com.google.android.gms.wearable.Wearable
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class SensorService : Service(), SensorEventListener {

    private val writeBuffer  = mutableListOf<String>()
    private val bufferLock   = Any()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var sensorManager: SensorManager
    private var accSensor:  Sensor? = null
    private var gyroSensor: Sensor? = null
    private var writer: BufferedWriter? = null

    private val channelId = "sensor_logging_channel"
    private val notifId   = 101

    private var sportCategory: String = "UNKNOWN"
    private var sessionId:     String = ""
    private var isLiveMode:    Boolean = false

    private lateinit var logsDir: File
    private var currentLogFile: File? = null

    private val accCount  = AtomicLong(0)
    private val gyroCount = AtomicLong(0)

    private val samplingPeriodUs = 20_000   // 50 Hz

    @Volatile private var isLoggingActive = false

    private var lastAx = Float.NaN; private var lastAy = Float.NaN; private var lastAz = Float.NaN
    private var lastAccEventTsNs: Long = 0L; private var hasFreshAcc = false

    private var lastGx = Float.NaN; private var lastGy = Float.NaN; private var lastGz = Float.NaN
    private var lastGyroEventTsNs: Long = 0L; private var hasFreshGyro = false

    private val pairThresholdNs = 10_000_000L

    // Live streaming: every 5th paired sample → ~10 Hz to phone chart
    private var liveStreamCounter = 0
    private val LIVE_STREAM_EVERY_N = 5
    @Volatile private var cachedPhoneNodeId: String? = null

    private val fileTsFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)

    private fun formatFileTimestamp(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(fileTsFormatter)

    private fun sanitize(s: String): String = s.replace(Regex("[^A-Za-z0-9_-]"), "_")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accSensor  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        logsDir    = File(filesDir, "logs").apply { mkdirs() }
        serviceScope.launch {
            while (isActive) { delay(200); flushBufferToDisk() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isLoggingActive) return START_STICKY

        sportCategory     = intent?.getStringExtra("sport")     ?: "UNKNOWN"
        sessionId         = intent?.getStringExtra("sessionId") ?: UUID.randomUUID().toString().replace("-","").take(12)
        isLiveMode        = intent?.getBooleanExtra("isLiveMode", false) ?: false
        liveStreamCounter = 0
        isLoggingActive   = true
        resetLatestSamples()

        startInForeground()
        try { initializeWriter() } catch (e: Exception) {
            Log.e(TAG, "Writer init failed", e); isLoggingActive = false; stopSelf()
            return START_NOT_STICKY
        }
        if (isLiveMode) refreshPhoneNode()
        registerSensors()
        Log.d(TAG, "Started sport=$sportCategory session=$sessionId liveMode=$isLiveMode")
        return START_STICKY
    }

    @SuppressLint("ForegroundServiceType")
    private fun startInForeground() {
        createNotificationChannelIfNeeded()
        val tag = if (isLiveMode) "[LIVE]" else "[HOME]"
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("MotionWatch $tag")
            .setContentText("Logging… ($sportCategory)")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true).setOnlyAlertOnce(true).build()
        startForeground(notifId, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(channelId, "Motion Logging", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun initializeWriter() {
        val logFile = File(logsDir, "SESSION_${sanitize(sessionId)}_WATCH_${sanitize(sportCategory)}_${formatFileTimestamp(System.currentTimeMillis())}.csv")
        currentLogFile = logFile
        writer = BufferedWriter(FileWriter(logFile, false)).apply {
            write("epoch_ms,event_ts_ns,AX,AY,AZ,GX,GY,GZ,label,sessionID\n"); flush()
        }
    }

    private fun registerSensors() {
        accSensor?.let  { sensorManager.registerListener(this, it, samplingPeriodUs) }
        gyroSensor?.let { sensorManager.registerListener(this, it, samplingPeriodUs) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAx = event.values[0]; lastAy = event.values[1]; lastAz = event.values[2]
                lastAccEventTsNs = event.timestamp; hasFreshAcc = true; accCount.incrementAndGet()
            }
            Sensor.TYPE_GYROSCOPE -> {
                lastGx = event.values[0]; lastGy = event.values[1]; lastGz = event.values[2]
                lastGyroEventTsNs = event.timestamp; hasFreshGyro = true; gyroCount.incrementAndGet()
                sendBroadcast(Intent(ACTION_GYRO_UPDATE).apply {
                    setPackage(packageName)
                    putExtra("gx", lastGx); putExtra("gy", lastGy); putExtra("gz", lastGz)
                })
            }
            else -> return
        }
        tryWriteMergedSample()
    }

    private fun tryWriteMergedSample() {
        if (!hasFreshAcc || !hasFreshGyro) return
        if (abs(lastAccEventTsNs - lastGyroEventTsNs) > pairThresholdNs) return

        val epochMs = System.currentTimeMillis()
        val line    = "$epochMs,${maxOf(lastAccEventTsNs,lastGyroEventTsNs)},$lastAx,$lastAy,$lastAz,$lastGx,$lastGy,$lastGz,$sportCategory,$sessionId\n"
        synchronized(bufferLock) { writeBuffer.add(line) }
        hasFreshAcc = false; hasFreshGyro = false

        // Live streaming: throttled to ~10 Hz
        if (isLiveMode) {
            if (++liveStreamCounter % LIVE_STREAM_EVERY_N == 0) {
                streamSampleToPhone(lastAx, lastAy, lastAz, lastGx, lastGy, lastGz)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ── Live streaming ────────────────────────────────────────────────────────

    private fun refreshPhoneNode() {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes -> cachedPhoneNodeId = nodes.firstOrNull()?.id }
            .addOnFailureListener { cachedPhoneNodeId = null }
    }

    /**
     * Send one IMU sample to the phone.
     * Path  : /data/sensor  (WearCommandService.PATH_SENSOR_DATA)
     * Format: "ax=…;ay=…;az=…;gx=…;gy=…;gz=…"
     *
     * WearCommandService broadcasts ACTION_SENSOR_DATA → AnalyticsFragment
     * feeds the data into the WACC / WGYRO chart tabs.
     */
    private fun streamSampleToPhone(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) {
        val nodeId = cachedPhoneNodeId ?: return
        val payload = "ax=$ax;ay=$ay;az=$az;gx=$gx;gy=$gy;gz=$gz"
        Wearable.getMessageClient(this)
            .sendMessage(nodeId, PATH_SENSOR_DATA, payload.toByteArray(Charsets.UTF_8))
            .addOnFailureListener { cachedPhoneNodeId = null; refreshPhoneNode() }
    }

    // ── Disk helpers ──────────────────────────────────────────────────────────

    private fun resetLatestSamples() {
        lastAx = Float.NaN; lastAy = Float.NaN; lastAz = Float.NaN
        lastGx = Float.NaN; lastGy = Float.NaN; lastGz = Float.NaN
        lastAccEventTsNs = 0L; lastGyroEventTsNs = 0L
        hasFreshAcc = false; hasFreshGyro = false
    }

    private fun flushBufferToDisk() {
        val copy: List<String> = synchronized(bufferLock) {
            if (writeBuffer.isEmpty()) return
            writeBuffer.toList().also { writeBuffer.clear() }
        }
        try { val w = writer ?: return; copy.forEach { w.write(it) }; w.flush() }
        catch (e: Exception) { Log.e(TAG, "flush failed", e) }
    }

    private fun sendFileToPhone(file: File, session: String) {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                val nodeId = nodes.firstOrNull()?.id ?: return@addOnSuccessListener
                Wearable.getChannelClient(this).openChannel(nodeId, "/file/$session/${file.name}")
                    .addOnSuccessListener { channel ->
                        Wearable.getChannelClient(this).getOutputStream(channel)
                            .addOnSuccessListener { stream ->
                                try { stream.use { out -> file.inputStream().use { it.copyTo(out) } }; file.delete() }
                                catch (e: Exception) { Log.e(TAG_SYNC, "Send failed", e) }
                                finally { Wearable.getChannelClient(this).close(channel) }
                            }
                    }
            }
    }

    override fun onDestroy() {
        isLoggingActive = false
        try { sensorManager.unregisterListener(this) } catch (_: Exception) {}
        try { flushBufferToDisk() } catch (_: Exception) {}
        try { writer?.flush(); writer?.close() } catch (_: Exception) {}
        writer = null
        serviceScope.cancel()

        // Send the CSV file to the phone regardless of mode.
        // Home mode: full-resolution recording. Live mode: same data that was
        // streamed at 10 Hz, but now at full 50 Hz resolution from the buffer.
        currentLogFile?.takeIf { it.exists() }?.let { sendFileToPhone(it, sessionId.ifBlank { "unknown" }) }

        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val TAG            = "SensorService"
        private const val TAG_SYNC       = "WearSync"
        const val ACTION_GYRO_UPDATE     = "com.example.motionwatch.GYRO_UPDATE"
        private const val PATH_SENSOR_DATA = "/data/sensor"
    }
}