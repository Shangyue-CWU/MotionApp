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

    private val writeBuffer = mutableListOf<String>()
    private val bufferLock = Any()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var sensorManager: SensorManager
    private var accSensor: Sensor? = null
    private var gyroSensor: Sensor? = null

    private var writer: BufferedWriter? = null

    private val channelId = "sensor_logging_channel"
    private val notifId = 101

    private var sportCategory: String = "UNKNOWN"
    private var sessionId: String = ""

    private lateinit var logsDir: File
    private var currentLogFile: File? = null

    private val accCount = AtomicLong(0)
    private val gyroCount = AtomicLong(0)

    // 20,000 us = 50 Hz
    private val samplingPeriodUs = 20_000

    @Volatile
    private var isLoggingActive = false

    // Latest accel sample
    private var lastAx = Float.NaN
    private var lastAy = Float.NaN
    private var lastAz = Float.NaN
    private var lastAccEventTsNs: Long = 0L
    private var hasFreshAcc = false

    // Latest gyro sample
    private var lastGx = Float.NaN
    private var lastGy = Float.NaN
    private var lastGz = Float.NaN
    private var lastGyroEventTsNs: Long = 0L
    private var hasFreshGyro = false

    // Pairing threshold: 10 ms
    private val pairThresholdNs = 10_000_000L

    private val fileTsFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)

    private fun formatFileTimestamp(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(fileTsFormatter)
    }

    private fun sanitizeForFilename(s: String): String {
        return s.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        logsDir = File(filesDir, "logs").apply { mkdirs() }

        serviceScope.launch {
            while (isActive) {
                delay(200)
                flushBufferToDisk()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isLoggingActive) {
            Log.w(TAG, "Service already logging; ignoring duplicate start")
            return START_STICKY
        }

        sportCategory = intent?.getStringExtra("sport") ?: "UNKNOWN"
        sessionId = intent?.getStringExtra("sessionId")
            ?: UUID.randomUUID().toString().replace("-", "").take(12)

        resetLatestSamples()
        isLoggingActive = true

        startInForeground(sportCategory, sessionId)

        try {
            initializeWriter(sportCategory, sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize writer", e)
            isLoggingActive = false
            stopSelf()
            return START_NOT_STICKY
        }

        registerSensors()

        Log.d(TAG, "Logging started. sport=$sportCategory session=$sessionId file=${currentLogFile?.name}")
        return START_STICKY
    }

    @SuppressLint("ForegroundServiceType")
    private fun startInForeground(sport: String, session: String) {
        createNotificationChannelIfNeeded()

        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("MotionWatch")
            .setContentText("Logging… ($sport) [$session]")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        startForeground(
            notifId,
            notif,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                channelId,
                "Motion Logging",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
    }

    private fun initializeWriter(label: String, session: String) {
        val startMs = System.currentTimeMillis()

        val safeLabel = sanitizeForFilename(label)
        val safeSession = sanitizeForFilename(session)
        val tsHuman = formatFileTimestamp(startMs)

        val logFile = File(logsDir, "SESSION_${safeSession}_WATCH_${safeLabel}_${tsHuman}.csv")
        currentLogFile = logFile

        writer = BufferedWriter(FileWriter(logFile, false)).apply {
            write("epoch_ms,event_ts_ns,AX,AY,AZ,GX,GY,GZ,label,sessionID\n")
            flush()
        }

        Log.d(TAG, "Created file: ${logFile.name}")
    }

    private fun registerSensors() {
        Log.d(TAG, "Registering sensors @ ${samplingPeriodUs}us (~${1000000.0 / samplingPeriodUs} Hz)")

        if (accSensor == null) {
            Log.e(TAG, "Accelerometer not available on this watch")
        } else {
            sensorManager.registerListener(this, accSensor, samplingPeriodUs)
            Log.d(TAG, "Accelerometer registered")
        }

        if (gyroSensor == null) {
            Log.e(TAG, "Gyroscope not available on this watch")
        } else {
            sensorManager.registerListener(this, gyroSensor, samplingPeriodUs)
            Log.d(TAG, "Gyroscope registered")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAx = event.values[0]
                lastAy = event.values[1]
                lastAz = event.values[2]
                lastAccEventTsNs = event.timestamp
                hasFreshAcc = true
                accCount.incrementAndGet()
            }

            Sensor.TYPE_GYROSCOPE -> {
                lastGx = event.values[0]
                lastGy = event.values[1]
                lastGz = event.values[2]
                lastGyroEventTsNs = event.timestamp
                hasFreshGyro = true
                gyroCount.incrementAndGet()

                val ui = Intent(ACTION_GYRO_UPDATE).apply {
                    setPackage(packageName)
                    putExtra("gx", lastGx)
                    putExtra("gy", lastGy)
                    putExtra("gz", lastGz)
                }
                sendBroadcast(ui)
            }

            else -> return
        }

        tryWriteMergedSample()
    }

    private fun tryWriteMergedSample() {
        if (!hasFreshAcc || !hasFreshGyro) return

        val dt = abs(lastAccEventTsNs - lastGyroEventTsNs)
        if (dt > pairThresholdNs) {
            // Too far apart in sensor time. Keep waiting for a closer match.
            // This avoids manufacturing mismatched rows.
            return
        }

        val epochMs = System.currentTimeMillis()
        val mergedEventTsNs = maxOf(lastAccEventTsNs, lastGyroEventTsNs)

        val line = "$epochMs,$mergedEventTsNs," +
                "$lastAx,$lastAy,$lastAz," +
                "$lastGx,$lastGy,$lastGz," +
                "$sportCategory,$sessionId\n"

        synchronized(bufferLock) {
            writeBuffer.add(line)
        }

        // Consume both fresh samples so we only write one row per paired update.
        hasFreshAcc = false
        hasFreshGyro = false
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun resetLatestSamples() {
        lastAx = Float.NaN
        lastAy = Float.NaN
        lastAz = Float.NaN
        lastGx = Float.NaN
        lastGy = Float.NaN
        lastGz = Float.NaN

        lastAccEventTsNs = 0L
        lastGyroEventTsNs = 0L

        hasFreshAcc = false
        hasFreshGyro = false
    }

    private fun flushBufferToDisk() {
        val localCopy: List<String> = synchronized(bufferLock) {
            if (writeBuffer.isEmpty()) return
            val copy = writeBuffer.toList()
            writeBuffer.clear()
            copy
        }

        try {
            val w = writer ?: return
            for (line in localCopy) {
                w.write(line)
            }
            w.flush()
        } catch (e: Exception) {
            Log.e(TAG, "flushBufferToDisk failed", e)
        }
    }

    private fun sendFileToPhone(file: File, session: String) {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Log.e(TAG_SYNC, "No connected phone nodes; cannot send ${file.name}")
                    return@addOnSuccessListener
                }

                val nodeId = nodes[0].id
                val path = "/file/$session/${file.name}"
                Log.d(TAG_SYNC, "Sending to node=$nodeId path=$path")

                Wearable.getChannelClient(this)
                    .openChannel(nodeId, path)
                    .addOnSuccessListener { channel ->
                        Wearable.getChannelClient(this)
                            .getOutputStream(channel)
                            .addOnSuccessListener { stream ->
                                try {
                                    stream.use { out ->
                                        file.inputStream().use { input ->
                                            input.copyTo(out)
                                        }
                                    }
                                    Log.d(TAG_SYNC, "Sent OK: ${file.name}")

                                    val deleted = try {
                                        file.delete()
                                    } catch (_: Exception) {
                                        false
                                    }
                                    Log.d(TAG_SYNC, "Delete after send: ${file.name} -> $deleted")

                                } catch (e: Exception) {
                                    Log.e(TAG_SYNC, "Send failed: ${file.name}", e)
                                } finally {
                                    Wearable.getChannelClient(this).close(channel)
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG_SYNC, "getOutputStream failed", e)
                                Wearable.getChannelClient(this).close(channel)
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG_SYNC, "openChannel failed", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG_SYNC, "connectedNodes failed", e)
            }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() acc=${accCount.get()} gyro=${gyroCount.get()} file=${currentLogFile?.name}")

        isLoggingActive = false

        try {
            sensorManager.unregisterListener(this)
        } catch (_: Exception) {
        }

        try {
            flushBufferToDisk()
        } catch (_: Exception) {
        }

        try {
            writer?.flush()
            writer?.close()
        } catch (_: Exception) {
        }
        writer = null

        serviceScope.cancel()

        currentLogFile?.let { file ->
            if (file.exists() && file.isFile) {
                sendFileToPhone(file, sessionId.ifBlank { "unknown" })
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SensorService"
        private const val TAG_SYNC = "WearSync"
        const val ACTION_GYRO_UPDATE = "com.example.motionwatch.GYRO_UPDATE"
    }
}