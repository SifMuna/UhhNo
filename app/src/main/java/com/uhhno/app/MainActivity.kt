package com.uhhno.app

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.*
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.uhhno.app.databinding.ActivityMainBinding
import org.vosk.Model
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), SpeechService.SpeechListener {

    private lateinit var binding: ActivityMainBinding
    private var speechService: SpeechService? = null
    private var audioRecorder: AudioRecorder? = null
    private val fillerAdapter = FillerLogAdapter()

    private var isListening = false
    private var modelReady = false
    private var model: Model? = null
    private lateinit var speechSettings: SpeechSettings
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
        speechSettings = SpeechSettings(this)
        setupRecyclerView()
        setupClickListeners()
        updateUI()
        initModel()
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
        binding.btnSettings.setOnClickListener {
            SettingsSheet().show(supportFragmentManager, "settings")
        }
        binding.btnClear.setOnClickListener {
            fillerCount = 0
            prevWordList = emptyList()
            fillerAdapter.clearAll()
            binding.tvLastFiller.text = ""
            binding.tvFillerCount.text = "0"
        }
    }

    // ── Model init ────────────────────────────────────────────────────────────

    private fun initModel() {
        binding.tvStatus.text = if (ModelLoader.isReady(this)) "Loading speech model…" else "Downloading speech model…"
        binding.btnMic.isEnabled = false

        ModelLoader.load(
            context = this,
            onProgress = { msg -> runOnUiThread { if (!isDestroyed) binding.tvStatus.text = msg } },
            onReady = { m ->
                runOnUiThread {
                    if (isDestroyed) { m.close(); return@runOnUiThread }
                    model = m
                    modelReady = true
                    updateUI()
                }
            },
            onError = { e ->
                runOnUiThread {
                    if (!isDestroyed) {
                        binding.tvStatus.text = "Model load failed"
                        Snackbar.make(binding.root, "Could not load speech model: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        )
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
        val m = model ?: return

        isListening = true
        fillerCount = 0
        prevWordList = emptyList()
        sessionStartMs = System.currentTimeMillis()
        fillerAdapter.clearAll()
        binding.tvLastFiller.text = ""

        updateUI()
        timerHandler.post(timerRunnable)

        speechService = SpeechService(this).also { it.start(m, speechSettings) }

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
        binding.btnMic.isEnabled = modelReady
        binding.btnSettings.isEnabled = !isListening
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
            if (modelReady) {
                binding.tvStatus.text = if (fillerCount > 0) "Session ended" else "Ready"
            }
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

        var divergeAt = minOf(prevWordList.size, curr.size)
        for (i in 0 until divergeAt) {
            if (prevWordList[i] != curr[i]) { divergeAt = i; break }
        }

        for (i in divergeAt until curr.size) {
            if (FillerDetector.isSingleWordFiller(curr[i])) onFillerDetected(curr[i])
        }

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
        Snackbar.make(binding.root, "Recognition error", Snackbar.LENGTH_SHORT).show()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        if (isListening) stopSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacksAndMessages(null)
        model?.close()
    }

    companion object {
        private const val RC_MIC = 1001
    }
}
