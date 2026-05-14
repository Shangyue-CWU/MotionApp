package com.example.motionwatch

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream

/**
 * MotionClassifier
 * ================
 * Loads a TorchScript Lite (.ptl) model from assets and runs 1-window inference.
 *
 * ── Assets required (app/src/main/assets/) ───────────────────────────────────
 *   best_cnn_baseline.ptl   OR   best_cnn_lstm.ptl
 *   scaler_params.json           (from Phase 1 dataset assembly output)
 *   label_encoder.json           (from Phase 1 dataset assembly output)
 *
 * ── Usage ────────────────────────────────────────────────────────────────────
 *   // In Application.onCreate() or lazily:
 *   val classifier = MotionClassifier(context, modelFileName = "best_cnn_baseline.ptl")
 *
 *   // In AnalyticsFragment.classifyWindow():
 *   val window: Array<FloatArray>   // [128][6] — 128 samples × 6 axes
 *   val result = classifier.classify(window)
 *   // result.label      → e.g. "TREMOR"
 *   // result.confidence → e.g. 0.87f
 *
 * ── Input shape ──────────────────────────────────────────────────────────────
 *   The model expects [1, 6, 128] (batch=1, channels=6, length=128).
 *   If you exported with --channels_last, set channelsLast=true in the constructor.
 *
 * ── Thread safety ────────────────────────────────────────────────────────────
 *   classify() is synchronised. Safe to call from any thread.
 */
class MotionClassifier(
    private val context: Context,
    private val modelFileName: String = "best_cnn_baseline.ptl",
    private val channelsLast:  Boolean = false   // true if model expects [1, 128, 6]
) {

    data class Result(val label: String, val confidence: Float)

    private val TAG = "MotionClassifier"

    private var module: Module? = null

    // Per-axis z-score parameters loaded from scaler_params.json
    // Order: AX, AY, AZ, GX, GY, GZ
    private val means = FloatArray(6)
    private val stds  = FloatArray(6) { 1f }

    // Index → label string from label_encoder.json
    private val labelMap = mutableMapOf<Int, String>()

    private val WINDOW_SIZE = 128
    private val NUM_AXES    = 6

    // ── Initialisation ────────────────────────────────────────────────────────

    init {
        try {
            loadModel()
            loadScaler()
            loadLabelEncoder()
            Log.i(TAG, "Initialised with $modelFileName, ${labelMap.size} classes")
        } catch (e: Exception) {
            Log.e(TAG, "Initialisation failed — classifier will return UNKNOWN", e)
        }
    }

    private fun loadModel() {
        val file = assetToTempFile(modelFileName)
        module  = LiteModuleLoader.load(file.absolutePath)
        Log.d(TAG, "Model loaded from $modelFileName")
    }

    /**
     * scaler_params.json format (produced by Phase 1 assembly):
     * {
     *   "AX_mean": 0.012, "AX_std": 1.234,
     *   "AY_mean": ...,   "AY_std": ...,
     *   ...
     *   "GZ_mean": ...,   "GZ_std": ...
     * }
     */
    private fun loadScaler() {
        val json = JSONObject(context.assets.open("scaler_params.json").bufferedReader().readText())
        val axes = listOf("AX", "AY", "AZ", "GX", "GY", "GZ")
        axes.forEachIndexed { i, ax ->
            means[i] = json.getDouble("${ax}_mean").toFloat()
            stds[i]  = json.getDouble("${ax}_std").toFloat().coerceAtLeast(1e-6f)
        }
        Log.d(TAG, "Scaler loaded. AX mean=${means[0]} std=${stds[0]}")
    }

    /**
     * Handles both JSON orientations:
     *   Forward:  { "0": "JERKING", "1": "TONIC", ... }   (index → name)
     *   Inverted: { "JERKING": 0,   "TONIC": 1,   ... }   (name → index)
     * The training code used the inverted format; this method detects and
     * flips it automatically so both work without changing the JSON file.
     */
    private fun loadLabelEncoder() {
        val json = JSONObject(context.assets.open("label_encoder.json").bufferedReader().readText())
        val firstKey = json.keys().next()
        if (firstKey.toIntOrNull() != null) {
            // Forward format: keys are integer strings → values are class names
            json.keys().forEach { key -> labelMap[key.toInt()] = json.getString(key) }
        } else {
            // Inverted format: keys are class names → values are integer indices
            json.keys().forEach { name -> labelMap[json.getInt(name)] = name }
        }
        Log.d(TAG, "Label encoder loaded (${labelMap.size} classes): $labelMap")
    }

    // ── Inference ─────────────────────────────────────────────────────────────

    /**
     * Classify one window.
     *
     * @param window  [128][6] float array — 128 consecutive samples,
     *                each sample = [AX, AY, AZ, GX, GY, GZ].
     *                Must have exactly WINDOW_SIZE rows.
     * @return Result with the top-1 label and its softmax probability.
     *         Returns Result("UNKNOWN", 0f) if the model is not loaded or
     *         the window is incomplete.
     */
    @Synchronized
    fun classify(window: Array<FloatArray>): Result {
        val mod = module ?: return Result(LABEL_UNKNOWN, 0f)
        if (window.size < WINDOW_SIZE) return Result(LABEL_UNKNOWN, 0f)

        return try {
            val inputTensor = buildInputTensor(window)
            val output      = mod.forward(IValue.from(inputTensor)).toTensor()
            val logits      = output.dataAsFloatArray           // [num_classes]
            val probs       = softmax(logits)
            val topIdx      = probs.indices.maxByOrNull { probs[it] } ?: 0
            val label       = labelMap[topIdx] ?: LABEL_UNKNOWN
            val confidence  = probs[topIdx]
            Result(label, confidence)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            Result(LABEL_UNKNOWN, 0f)
        }
    }

    /**
     * Build the input tensor from the raw window, applying z-score normalisation.
     *
     * channelsLast=false (default):
     *   Shape [1, 6, 128] — PyTorch Conv1d channels-first convention.
     *   data[channel * 128 + time] = (window[time][channel] - mean) / std
     *
     * channelsLast=true:
     *   Shape [1, 128, 6]
     *   data[time * 6 + channel] = (window[time][channel] - mean) / std
     */
    private fun buildInputTensor(window: Array<FloatArray>): Tensor {
        val data: FloatArray

        if (channelsLast) {
            // Shape [1, 128, 6]
            data = FloatArray(WINDOW_SIZE * NUM_AXES)
            for (t in 0 until WINDOW_SIZE) {
                for (c in 0 until NUM_AXES) {
                    data[t * NUM_AXES + c] = (window[t][c] - means[c]) / stds[c]
                }
            }
            return Tensor.fromBlob(data, longArrayOf(1, WINDOW_SIZE.toLong(), NUM_AXES.toLong()))
        } else {
            // Shape [1, 6, 128]
            data = FloatArray(NUM_AXES * WINDOW_SIZE)
            for (c in 0 until NUM_AXES) {
                for (t in 0 until WINDOW_SIZE) {
                    data[c * WINDOW_SIZE + t] = (window[t][c] - means[c]) / stds[c]
                }
            }
            return Tensor.fromBlob(data, longArrayOf(1, NUM_AXES.toLong(), WINDOW_SIZE.toLong()))
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun softmax(logits: FloatArray): FloatArray {
        val max  = logits.max()
        val exps = logits.map { Math.exp((it - max).toDouble()).toFloat() }
        val sum  = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }

    /**
     * PyTorch Mobile requires the model file to exist on the real filesystem,
     * not inside the APK assets zip. Copy it to the app's cache directory.
     */
    private fun assetToTempFile(assetName: String): File {
        val out = File(context.cacheDir, assetName)
        if (!out.exists()) {
            context.assets.open(assetName).use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
        }
        return out
    }

    fun release() {
        module?.destroy()
        module = null
    }

    companion object {
        const val LABEL_UNKNOWN = "UNKNOWN"
    }
}