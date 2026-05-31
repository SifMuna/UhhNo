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

    fun start(model: Model) {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, CHUNK_SAMPLES * 2 * 4)   // at least 4 chunks of headroom
        ).also { it.startRecording() }

        active = true
        captureThread = Thread(::captureLoop, "vosk-capture")
        captureThread!!.start()
    }

    private fun captureLoop() {
        val rec = recognizer ?: return
        val ar = audioRecord ?: return
        val buf = ShortArray(CHUNK_SAMPLES)

        var lastPartial = ""
        var voicedMs = 0L
        var lastTime = System.currentTimeMillis()
        var hesitationFired = false

        while (active) {
            val n = ar.read(buf, 0, buf.size)
            if (n < 0) break

            val now = System.currentTimeMillis()
            val elapsed = now - lastTime
            lastTime = now

            val isVoiced = rms(buf, n) > SPEECH_THRESHOLD

            if (rec.acceptWaveForm(buf, n)) {
                // Utterance boundary: run detection on final text, then reset state.
                val text = parse(rec.result, "text")
                Log.d(TAG, "result: $text")
                lastPartial = ""
                voicedMs = 0
                hesitationFired = false
                mainHandler.post {
                    if (text.isNotBlank()) listener.onPartialResult(text)
                    listener.onResult(text)
                }
            } else {
                val partial = parse(rec.partialResult, "partial")

                // "i'm" is the small model's consistent mistranscription of "umm".
                // Treat it the same as an empty partial for hesitation detection.
                val noWords = partial.isBlank() || partial.trim().equals("i'm", ignoreCase = true)

                if (isVoiced && noWords && !hesitationFired) {
                    voicedMs += elapsed
                    if (voicedMs >= HESITATION_MS) {
                        hesitationFired = true
                        Log.d(TAG, "hesitation: ${voicedMs}ms voiced with no recognized words")
                        mainHandler.post { listener.onPartialResult("uh") }
                    }
                } else if (!noWords) {
                    // Real words appeared — reset the hesitation window.
                    voicedMs = 0
                }

                if (partial.isNotBlank() && partial != lastPartial) {
                    lastPartial = partial
                    Log.d(TAG, "partial: $partial")
                    mainHandler.post { listener.onPartialResult(partial) }
                }
            }
        }

        // Flush any remaining audio at the end of the session.
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
        audioRecord?.stop()        // makes ar.read() return an error, breaking the loop
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
        private const val SPEECH_THRESHOLD = 600f       // RMS threshold on [-32768, 32767] scale
        private const val HESITATION_MS = 500L          // voiced audio with no words before firing
    }
}
