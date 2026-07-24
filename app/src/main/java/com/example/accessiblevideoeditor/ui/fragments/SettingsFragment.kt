package com.example.accessiblevideoeditor.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import com.example.accessiblevideoeditor.databinding.FragmentSettingsBinding
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.ui.LanguageManager
import com.example.accessiblevideoeditor.ui.SettingsManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Load current settings
        binding.switchStartupSound.isChecked = SettingsManager.isStartupSoundEnabled
        binding.switchProcessingSound.isChecked = SettingsManager.isProcessingSoundEnabled
        binding.switchSuccessSound.isChecked = SettingsManager.isSuccessSoundEnabled
        binding.switchErrorSound.isChecked = SettingsManager.isErrorSoundEnabled
        binding.switchDarkMode.isChecked = SettingsManager.isDarkMode

        binding.etGeminiApiKey.setText(SettingsManager.geminiApiKey)

        // Setup AI Model Spinner
        val models = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash")
        val modelAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, models)
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spGeminiModel.adapter = modelAdapter
        val modelIndex = models.indexOf(SettingsManager.geminiModel)
        if (modelIndex >= 0) binding.spGeminiModel.setSelection(modelIndex)

        // Setup Language Spinner
        val languages = LanguageManager.supportedLanguages.map { it.second }
        val langAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spLanguage.adapter = langAdapter
        val currentLangIndex = LanguageManager.supportedLanguages.indexOfFirst { it.first == LanguageManager.getCurrentLanguageCode() }
        if (currentLangIndex >= 0) binding.spLanguage.setSelection(currentLangIndex)

        // Setup Listeners
        binding.switchStartupSound.setOnCheckedChangeListener { _, isChecked -> SettingsManager.isStartupSoundEnabled = isChecked }
        binding.switchProcessingSound.setOnCheckedChangeListener { _, isChecked -> SettingsManager.isProcessingSoundEnabled = isChecked }
        binding.switchSuccessSound.setOnCheckedChangeListener { _, isChecked -> SettingsManager.isSuccessSoundEnabled = isChecked }
        binding.switchErrorSound.setOnCheckedChangeListener { _, isChecked -> SettingsManager.isErrorSoundEnabled = isChecked }
        
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked -> 
            SettingsManager.isDarkMode = isChecked 
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        binding.etGeminiApiKey.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                SettingsManager.geminiApiKey = s.toString()
            }
        })

        binding.spGeminiModel.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                SettingsManager.geminiModel = models[position]
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.spLanguage.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCode = LanguageManager.supportedLanguages[position].first
                if (selectedCode != LanguageManager.getCurrentLanguageCode()) {
                    LanguageManager.setLanguage(selectedCode)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.btnViewErrorLog.setOnClickListener {
            val safeCtx = context ?: return@setOnClickListener
            val logs = com.example.accessiblevideoeditor.utils.ErrorLogger.getLogContent(safeCtx)
            
            val scrollView = android.widget.ScrollView(safeCtx)
            val textView = android.widget.TextView(safeCtx).apply {
                text = logs
                setPadding(32, 32, 32, 32)
                setTextIsSelectable(true)
                typeface = android.graphics.Typeface.MONOSPACE
            }
            scrollView.addView(textView)
            
            androidx.appcompat.app.AlertDialog.Builder(safeCtx)
                .setTitle(getString(R.string.title_error_log))
                .setView(scrollView)
                .setPositiveButton(getString(R.string.btn_copy_all)) { d, _ ->
                    d.dismiss()
                    val clipboard = safeCtx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Error Logs", logs)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(safeCtx, getString(R.string.msg_log_copied), android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton(getString(R.string.btn_clear_log)) { d, _ ->
                    d.dismiss()
                    com.example.accessiblevideoeditor.utils.ErrorLogger.clearLog(safeCtx)
                    android.widget.Toast.makeText(safeCtx, getString(R.string.msg_log_cleared), android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(getString(R.string.btn_close)) { d, _ -> d.dismiss() }
                .show()
        }

        binding.btnHelp.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_helpFragment)
        }
        
        binding.btnVolunteerTranslation.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_volunteerTranslationFragment)
        }

        binding.btnCheckUpdates.setOnClickListener {
            val safeCtx = context ?: return@setOnClickListener
            try {
                android.widget.Toast.makeText(safeCtx, "جاري التحقق من وجود تحديثات...", android.widget.Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}

            viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                try {
                    val currentContext = context ?: return@launch
                    val currentActivity = activity ?: return@launch
                    if (!isAdded || currentActivity.isFinishing || currentActivity.isDestroyed) return@launch

                    val info = com.example.accessiblevideoeditor.updater.AppUpdater.checkForUpdate(currentContext)

                    val activeContext = context ?: return@launch
                    val activeActivity = activity ?: return@launch
                    if (!isAdded || activeActivity.isFinishing || activeActivity.isDestroyed) return@launch

                    if (info != null) {
                        com.example.accessiblevideoeditor.updater.AppUpdater.showUpdateDialog(activeActivity, info)
                        com.example.accessiblevideoeditor.updater.AppUpdater.showUpdateNotification(activeContext, info)
                    } else {
                        androidx.appcompat.app.AlertDialog.Builder(activeActivity)
                            .setTitle("التحقق من التحديثات")
                            .setMessage("تطبيقك محدث لأحدث إصدار أصلي (${com.example.accessiblevideoeditor.BuildConfig.VERSION_NAME}). لا توجد تحديثات جديدة حالياً.")
                            .setPositiveButton("موافق") { d, _ -> d.dismiss() }
                            .show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        binding.btnEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:support@accessiblevideoeditor.com")
            }
            startActivity(Intent.createChooser(intent, "Email"))
        }

        binding.btnWhatsApp.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/1234567890"))
            startActivity(intent)
        }

        binding.btnTwitter.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/YourAppHandler"))
            startActivity(intent)
        }

        binding.btnFacebook.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://facebook.com/YourAppPage"))
            startActivity(intent)
        }

        binding.btnPrivacyPolicy.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://raw.githubusercontent.com/abdobasha342/Accessible-Video-Editor/refs/heads/master/Privacy-policy.md"))
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
