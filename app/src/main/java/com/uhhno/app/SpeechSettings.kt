package com.uhhno.app

import android.content.Context

class SpeechSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 0 = least sensitive (high RMS threshold), 10 = most sensitive (low threshold).
    var sensitivityLevel: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY_LEVEL)
        set(v) { prefs.edit().putFloat(KEY_SENSITIVITY, v).apply() }

    // Derived from sensitivityLevel — used by SpeechService directly.
    val threshold: Float
        get() = THRESHOLD_MAX - sensitivityLevel * THRESHOLD_STEP

    var hesitationMs: Long
        get() = prefs.getLong(KEY_HESITATION_MS, DEFAULT_HESITATION_MS)
        set(v) { prefs.edit().putLong(KEY_HESITATION_MS, v).apply() }

    companion object {
        private const val PREFS_NAME = "speech_settings"
        private const val KEY_SENSITIVITY = "sensitivity_level"
        private const val KEY_HESITATION_MS = "hesitation_ms"

        // sensitivityLevel=8 → threshold = 2000 − 8×170 = 640, close to the old hardcoded 600.
        const val DEFAULT_SENSITIVITY_LEVEL = 8f
        const val DEFAULT_HESITATION_MS = 500L

        const val SENSITIVITY_MIN = 0f
        const val SENSITIVITY_MAX = 10f
        const val HESITATION_MS_MIN = 300L
        const val HESITATION_MS_MAX = 800L

        private const val THRESHOLD_MAX = 2000f
        private const val THRESHOLD_MIN = 300f
        private const val THRESHOLD_STEP = (THRESHOLD_MAX - THRESHOLD_MIN) / SENSITIVITY_MAX
    }
}
