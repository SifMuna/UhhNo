package com.uhhno.app

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputPath: String? = null

    fun start(): Boolean {
        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            outputPath = "${dir?.absolutePath}/UhhNo_$timestamp.m4a"

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder!!.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputPath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            false
        }
    }

    /** Stops recording and returns the saved file path, or null on failure. */
    fun stop(): String? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            outputPath
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            null
        }
    }
}
