package com.uhhno.app

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.uhhno.app.databinding.BottomSheetSettingsBinding

class SettingsSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).also { dialog ->
            dialog.setOnShowListener {
                val sheet = dialog.findViewById<View>(
                    com.google.android.material.R.id.design_bottom_sheet
                )
                sheet?.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.background)
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val settings = SpeechSettings(requireContext())

        binding.sliderSensitivity.value = settings.sensitivityLevel
        updateSensitivityLabel(settings.sensitivityLevel)
        binding.sliderSensitivity.addOnChangeListener { _, value, _ ->
            settings.sensitivityLevel = value
            updateSensitivityLabel(value)
        }

        binding.sliderHesitation.value = settings.hesitationMs.toFloat()
        updateHesitationLabel(settings.hesitationMs)
        binding.sliderHesitation.addOnChangeListener { _, value, _ ->
            val ms = value.toLong()
            settings.hesitationMs = ms
            updateHesitationLabel(ms)
        }
    }

    private fun updateSensitivityLabel(level: Float) {
        binding.tvSensitivityLabel.text = "Sensitivity · ${level.toInt()}/10"
    }

    private fun updateHesitationLabel(ms: Long) {
        binding.tvHesitationLabel.text = "Min hesitation · $ms ms"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
