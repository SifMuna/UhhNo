package com.uhhno.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class SpeechService(
    private val context: Context,
    private val listener: SpeechListener
) {
    interface SpeechListener {
        fun onPartialResult(text: String)
        fun onResult(text: String)
        fun onError(error: Int)
    }

    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start() {
        active = true
        startRecognizing()
    }

    fun stop() {
        active = false
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }

    private fun startRecognizing() {
        if (!active) return
        recognizer?.destroy()
        recognizer = null

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onError(-1)
            return
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: return
                    listener.onPartialResult(text)
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    listener.onResult(text)
                    if (active) scheduleRestart(100)
                }

                override fun onError(error: Int) {
                    listener.onError(error)
                    if (!active || error == SpeechRecognizer.ERROR_CLIENT) return
                    val delay = when (error) {
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 800L
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 2000L
                        else -> 200L
                    }
                    scheduleRestart(delay)
                }
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    private fun scheduleRestart(delayMs: Long) {
        mainHandler.postDelayed({ startRecognizing() }, delayMs)
    }
}
