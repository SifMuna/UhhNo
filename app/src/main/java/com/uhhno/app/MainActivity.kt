package com.uhhno.app

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.*
import android.speech.SpeechRecognizer
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.uhhno.app.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), SpeechService.SpeechListener {

    private lateinit var binding: ActivityMainBinding
    private var speechService: SpeechService? = null
    private var audioRecorder: AudioRecorder? = null
    private val fillerAdapter = FillerLogAdapter()

    private var isListening = false
    private var fillerCount = 0
    private var sessionStartMs = 0L
    private val timerHandler = Handler(Looper.getMainLooper())
    private var prevWordList = listOf<String>()

    private val timerRunnable = object : Runnable {
        override fun run() {
            val elapsed = (System.currentTimeMillis() - sessionStartMs) / 1000
            binding.tvDuration.text = String.format("%d:%02d", elapsed / 60, elapsed % 60)
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupRecyclerView()
        setupClickListeners()
        updateUI()
    }

    private fun setupRecyclerView() {
        val lm = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvFillerLog.layoutManager = lm
        binding.rvFillerLog.adapter = fillerAdapter
    }

    private fun setupClickListeners() {
        binding.btnMic.setOnClickListener {
            if (isListening) stopSession() else checkPermissionAndStart()
        }
        binding.btnClear.setOnClickListener {
            fillerCount = 0
            prevWordList = emptyList()
            fillerAdapter.clearAll()
            binding.tvLastFiller.text = ""
            binding.tvFillerCount.text = "0"
        }
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startSession()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), RC_MIC
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_MIC) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startSession()
            } else {
                Snackbar.make(binding.root, "Microphone permission required", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    private fun startSession() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            AlertDialog.Builder(this)
                .setTitle("Speech Recognition Unavailable")
                .setMessage("This device doesn't have a compatible speech recognition service. Install Google app or check your device settings.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        isListening = true
        fillerCount = 0
        prevWordList = emptyList()
        sessionStartMs = System.currentTimeMillis()
        fillerAdapter.clearAll()
        binding.tvLastFiller.text = ""

        updateUI()
        timerHandler.post(timerRunnable)

        speechService = SpeechService(this, this)
        speechService?.start()

        if (binding.switchRecord.isChecked) {
            audioRecorder = AudioRecorder(this)
            audioRecorder?.start()
        }
    }

    private fun stopSession() {
        isListening = false
        timerHandler.removeCallbacks(timerRunnable)

        speechService?.stop()
        speechService = null

        val savedPath = audioRecorder?.stop()
        audioRecorder = null

        updateUI()

        if (savedPath != null) {
            Snackbar.make(
                binding.root,
                "Recording saved: ${savedPath.substringAfterLast("/")}",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun updateUI() {
        binding.tvFillerCount.text = fillerCount.toString()
        if (isListening) {
            binding.btnMic.text = "STOP"
            binding.btnMic.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.filler_color)
            binding.tvStatus.text = "Listening…"
            binding.switchRecord.isEnabled = false
        } else {
            binding.btnMic.text = "TAP TO START"
            binding.btnMic.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.accent)
            binding.tvStatus.text = if (fillerCount > 0) "Session ended" else "Ready"
            binding.tvCurrentText.text = ""
            binding.switchRecord.isEnabled = true
        }
    }

    // ── Filler detected ───────────────────────────────────────────────────────

    private fun onFillerDetected(word: String) {
        fillerCount++
        binding.tvFillerCount.text = fillerCount.toString()
        binding.tvLastFiller.text = "\"${word.uppercase()}\""

        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        fillerAdapter.addFiller(FillerEntry(word, ts))
        binding.rvFillerLog.smoothScrollToPosition(fillerAdapter.itemCount - 1)

        // Red flash overlay
        binding.flashOverlay.animate().cancel()
        binding.flashOverlay.alpha = 0.4f
        binding.flashOverlay.animate()
            .alpha(0f)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Bounce the counter
        val up = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.tvFillerCount, "scaleX", 1f, 1.35f),
                ObjectAnimator.ofFloat(binding.tvFillerCount, "scaleY", 1f, 1.35f)
            )
            duration = 90
        }
        val down = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.tvFillerCount, "scaleX", 1.35f, 1f),
                ObjectAnimator.ofFloat(binding.tvFillerCount, "scaleY", 1.35f, 1f)
            )
            duration = 160
            interpolator = DecelerateInterpolator()
        }
        AnimatorSet().apply { playSequentially(up, down); start() }

        vibrate(60)
    }

    private fun vibrate(ms: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(ms)
                }
            }
        } catch (_: Exception) {}
    }

    // ── SpeechService.SpeechListener ──────────────────────────────────────────

    override fun onPartialResult(text: String) {
        binding.tvCurrentText.text = text
        val curr = text.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

        // Find where the word list diverges from the previous partial
        var divergeAt = minOf(prevWordList.size, curr.size)
        for (i in 0 until divergeAt) {
            if (prevWordList[i] != curr[i]) { divergeAt = i; break }
        }

        // Single-word fillers among newly arrived words
        for (i in divergeAt until curr.size) {
            if (FillerDetector.isSingleWordFiller(curr[i])) onFillerDetected(curr[i])
        }

        // Two-word fillers: check bigrams where the second word is new
        val bigramFrom = maxOf(0, divergeAt - 1)
        for (i in bigramFrom until curr.size - 1) {
            if (i + 1 >= divergeAt) {
                val bigram = "${curr[i]} ${curr[i + 1]}"
                if (FillerDetector.isTwoWordFiller(bigram)) onFillerDetected(bigram)
            }
        }

        prevWordList = curr
    }

    override fun onResult(text: String) {
        prevWordList = emptyList()
        binding.tvCurrentText.text = ""
    }

    override fun onError(error: Int) {
        // SpeechService auto-restarts; surface only fatal errors
        if (error == -1) {
            runOnUiThread {
                Snackbar.make(binding.root, "Speech recognition unavailable", Snackbar.LENGTH_LONG).show()
                if (isListening) stopSession()
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        if (isListening) stopSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val RC_MIC = 1001
    }
}
