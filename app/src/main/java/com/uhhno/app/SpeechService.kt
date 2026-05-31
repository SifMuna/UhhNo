package com.uhhno.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import kotlin.math.sqrt

class SpeechService(private val listener: SpeechListener) {

    interface SpeechListener {
        fun onPartialResult(text: String)
        fun onResult(text: String)
        fun onError(error: Int)
    }

    private var audioRecord: AudioRecord? = null
    private var recognizer: Recognizer? = null
    private var captureThread: Thread? = null
    @Volatile private var active = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start(model: Model, settings: SpeechSettings) {
        val threshold = settings.threshold
        val hesitationMs = settings.hesitationMs
        Log.d(TAG, "start: threshold=$threshold  hesitationMs=${hesitationMs}ms")

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, CHUNK_SAMPLES * 2 * 4)
        ).also { it.startRecording() }

        active = true
        captureThread = Thread({ captureLoop(threshold, hesitationMs) }, "vosk-capture")
        captureThread!!.start()
    }

    private fun captureLoop(speechThreshold: Float, hesitationMs: Long) {
        val rec = recognizer ?: return
        val ar = audioRecord ?: return
        val buf = ShortArray(CHUNK_SAMPLES)

        var lastPartial = ""
        var voicedMs = 0L
        var silenceMs = 0L
        var lastTime = System.currentTimeMillis()
        var hesitationFired = false

        while (active) {
            val n = ar.read(buf, 0, buf.size)
            if (n < 0) break

            val now = System.currentTimeMillis()
            val elapsed = now - lastTime
            lastTime = now

            val isVoiced = rms(buf, n) > speechThreshold

            if (rec.acceptWaveForm(buf, n)) {
                val text = parse(rec.result, "text")
                Log.d(TAG, "result: $text")
                lastPartial = ""
                voicedMs = 0
                silenceMs = 0
                hesitationFired = false
                mainHandler.post {
                    if (text.isNotBlank()) listener.onPartialResult(text)
                    listener.onResult(text)
                }
            } else {
                val partial = parse(rec.partialResult, "partial")
                val noWords = partial.isBlank() ||
                        partial.trim().equals("i'm", ignoreCase = true)

                if (!isVoiced) {
                    // Silence: accumulate debounce timer; reset voicedMs after a sustained gap.
                    silenceMs += elapsed
                    if (silenceMs >= SILENCE_DEBOUNCE_MS) voicedMs = 0
                } else if (noWords && !hesitationFired) {
                    // Voiced with no recognized words: accumulate toward hesitation threshold.
                    silenceMs = 0
                    voicedMs += elapsed
                    if (voicedMs >= hesitationMs) {
                        hesitationFired = true
                        voicedMs = 0
                        Log.d(TAG, "hesitation detected after ${hesitationMs}ms voiced with no words")
                        mainHandler.post { listener.onPartialResult("uh") }
                    }
                } else {
                    // Either words appeared or hesitation already fired — reset windows.
                    silenceMs = 0
                    if (!noWords) voicedMs = 0
                }

                if (partial.isNotBlank() && partial != lastPartial) {
                    lastPartial = partial
                    Log.d(TAG, "partial: $partial")
                    mainHandler.post { listener.onPartialResult(partial) }
                }
            }
        }

        val finalText = parse(rec.finalResult, "text")
        if (finalText.isNotBlank()) {
            mainHandler.post {
                listener.onPartialResult(finalText)
                listener.onResult(finalText)
            }
        }
    }

    fun stop() {
        active = false
        audioRecord?.stop()
        captureThread?.join(1000)
        captureThread = null
        audioRecord?.release()
        audioRecord = null
        recognizer?.close()
        recognizer = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun rms(buf: ShortArray, count: Int): Float {
        var sum = 0.0
        for (i in 0 until count) { val s = buf[i].toDouble(); sum += s * s }
        return sqrt(sum / count).toFloat()
    }

    private fun parse(json: String?, key: String): String =
        if (json.isNullOrBlank()) ""
        else try { JSONObject(json).optString(key, "") } catch (_: Exception) { "" }

    companion object {
        private const val TAG = "UhhNo"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SAMPLES = 1600          // 100 ms per chunk at 16 kHz
        private const val SILENCE_DEBOUNCE_MS = 150L    // gap before voicedMs resets
    }
}
