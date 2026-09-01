package com.example.accessiblevideoeditor.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentVolunteerTranslationBinding
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.LanguageManager
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class VolunteerTranslationFragment : Fragment() {

    private var _binding: FragmentVolunteerTranslationBinding? = null
    private val binding get() = _binding!!

    private val SOURCE_LOCAL = 0
    private val SOURCE_CLOUD = 1
    private var currentSource = SOURCE_LOCAL

    private var selectedLangCode = "en"
    private var selectedCategory = ""
    
    private val originalStrings = mutableMapOf<String, String>()
    private val categorizedStrings = mutableMapOf<String, List<String>>()
    
    private val localTranslations = mutableMapOf<String, String>()
    private val cloudTranslations = mutableMapOf<String, String>()
    private val translations = mutableMapOf<String, String>()
    
    private var categories = listOf<String>()
    private var lastBeepPercent = -5

    private lateinit var adapter: TranslationAdapter

    private val supportedLanguages = listOf(
        "ar" to "العربية",
        "en" to "الإنجليزية",
        "fr" to "الفرنسية",
        "es" to "الإسبانية",
        "zh-CN" to "الصينية",
        "ru" to "الروسية",
        "ja" to "اليابانية",
        "he" to "العبرية",
        "fa" to "الفارسية",
        "ur" to "الأردية",
        "tr" to "التركية"
    )

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = JSONObject()
                translations.forEach { (k, v) -> 
                    if (v.isNotBlank()) json.put(k, v) 
                }
                requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toString(4).toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_258), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_259), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    val jsonString = input.bufferedReader().use { it.readText() }
                    val json = JSONObject(jsonString)
                    
                    var valid = false
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        if (keys.next().startsWith("string_")) {
                            valid = true
                            break
                        }
                    }
                    
                    if (valid) {
                        val fileName = if (currentSource == SOURCE_LOCAL) "local_lang_$selectedLangCode.json" else "cloud_lang_$selectedLangCode.json"
                        val file = File(requireContext().filesDir, fileName)
                        file.writeText(jsonString)
                        
                        // Reload AppStrings
                        AppStrings.loadCustomStrings(requireContext())
                        
                        val newKeys = json.keys()
                        while (newKeys.hasNext()) {
                            val k = newKeys.next()
                            val v = json.getString(k)
                            translations[k] = v
                            if (currentSource == SOURCE_LOCAL) {
                                localTranslations[k] = v
                            } else {
                                cloudTranslations[k] = v
                            }
                        }
                        
                        Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_260), Toast.LENGTH_SHORT).show()
                        loadData() // Refresh list
                    } else {
                        Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_261), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_262), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVolunteerTranslationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_import -> {
                    importLauncher.launch(arrayOf("application/json", "*/*"))
                    true
                }
                R.id.action_save -> {
                    saveAndApply()
                    true
                }
                R.id.action_export -> {
                    val fileName = if (currentSource == SOURCE_LOCAL) "local_translations_${selectedLangCode}.json" else "cloud_translations_${selectedLangCode}.json"
                    exportLauncher.launch(fileName)
                    true
                }
                R.id.action_sync -> {
                    syncFromLocal()
                    true
                }
                else -> false
            }
        }

        setupLanguageSpinner()
        setupSourceTabs()
        
        binding.rvTranslations.layoutManager = LinearLayoutManager(requireContext())
        adapter = TranslationAdapter(
            requireContext(),
            emptyList(),
            originalStrings,
            translations,
            localTranslations,
            isCloudSource = (currentSource == SOURCE_CLOUD),
            onApplySuggestion = { key, suggestion ->
                translations[key] = suggestion
                cloudTranslations[key] = suggestion
                val keys = categorizedStrings[selectedCategory] ?: emptyList()
                val idx = keys.indexOf(key)
                if (idx >= 0) {
                    adapter.notifyItemChanged(idx)
                }
                calculateProgress()
            }
        ) { key, newText ->
            translations[key] = newText
            if (currentSource == SOURCE_LOCAL) {
                localTranslations[key] = newText
            } else {
                cloudTranslations[key] = newText
            }
            updateProgressText()
        }
        binding.rvTranslations.adapter = adapter
        
        binding.tabCategories.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab != null) {
                    selectedCategory = categories[tab.position]
                    updateList()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        loadData()
    }

    private fun setupSourceTabs() {
        val localTab = binding.tabTranslationSource.newTab().setText(AppStrings.get(requireContext(), R.string.string_translation_local))
        val cloudTab = binding.tabTranslationSource.newTab().setText(AppStrings.get(requireContext(), R.string.string_translation_cloud))
        
        binding.tabTranslationSource.addTab(localTab)
        binding.tabTranslationSource.addTab(cloudTab)
        binding.tabTranslationSource.selectTab(localTab)
        binding.tvTranslationModeDescription.text = AppStrings.get(requireContext(), R.string.msg_translation_local_desc)

        binding.tabTranslationSource.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab != null) {
                    currentSource = if (tab.position == 1) SOURCE_CLOUD else SOURCE_LOCAL
                    
                    val descRes = if (currentSource == SOURCE_LOCAL) R.string.msg_translation_local_desc else R.string.msg_translation_cloud_desc
                    binding.tvTranslationModeDescription.text = AppStrings.get(requireContext(), descRes)

                    // Switch current active translations reference
                    translations.clear()
                    if (currentSource == SOURCE_LOCAL) {
                        translations.putAll(localTranslations)
                    } else {
                        translations.putAll(cloudTranslations)
                    }

                    // Update adapter settings
                    adapter.updateData(
                        newIsCloudSource = (currentSource == SOURCE_CLOUD),
                        newTranslations = translations,
                        newLocalTranslations = localTranslations
                    )

                    // Show/hide sync menu item
                    val syncItem = binding.topAppBar.menu.findItem(R.id.action_sync)
                    syncItem?.isVisible = (currentSource == SOURCE_CLOUD)

                    updateList()
                    calculateProgress()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupLanguageSpinner() {
        val names = supportedLanguages.map { it.second }
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, names)
        binding.spLanguage.adapter = spinnerAdapter
        
        val initialIndex = supportedLanguages.indexOfFirst { it.first == "en" }
        if (initialIndex >= 0) binding.spLanguage.setSelection(initialIndex)

        binding.spLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedLangCode = supportedLanguages[position].first
                loadData()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadData() {
        binding.progressBar.visibility = View.VISIBLE
        binding.contentContainer.visibility = View.GONE
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val localFile = File(requireContext().filesDir, "local_lang_$selectedLangCode.json")
            val cloudFile = File(requireContext().filesDir, "cloud_lang_$selectedLangCode.json")
            val customFile = File(requireContext().filesDir, "custom_lang_$selectedLangCode.json")
            
            val tempLocal = mutableMapOf<String, String>()
            val tempCloud = mutableMapOf<String, String>()

            // 1. Load cloud translations
            if (cloudFile.exists()) {
                try {
                    val json = JSONObject(cloudFile.readText(Charsets.UTF_8))
                    for (key in json.keys()) {
                        tempCloud[key] = json.getString(key)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            // 2. Load local translations
            if (localFile.exists()) {
                try {
                    val json = JSONObject(localFile.readText(Charsets.UTF_8))
                    for (key in json.keys()) {
                        tempLocal[key] = json.getString(key)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            // 3. Load old custom_lang file for backward compatibility
            if (customFile.exists() && !localFile.exists()) {
                try {
                    val json = JSONObject(customFile.readText(Charsets.UTF_8))
                    for (key in json.keys()) {
                        tempLocal[key] = json.getString(key)
                    }
                    customFile.renameTo(localFile)
                } catch (e: Exception) { e.printStackTrace() }
            }

            localTranslations.clear()
            localTranslations.putAll(tempLocal)

            cloudTranslations.clear()
            cloudTranslations.putAll(tempCloud)

            translations.clear()
            if (currentSource == SOURCE_LOCAL) {
                translations.putAll(localTranslations)
            } else {
                translations.putAll(cloudTranslations)
            }

            originalStrings.clear()
            val catMap = mutableMapOf<String, MutableList<String>>()
            
            val fields = R.string::class.java.fields
            fields.forEach { field ->
                try {
                    if (field.name.startsWith("string_")) {
                        val id = field.getInt(null)
                        val keyName = field.name
                        val arText = requireContext().getString(id)
                        originalStrings[keyName] = arText
                        
                        val cat = getCategoryArabic(arText)
                        if (!catMap.containsKey(cat)) catMap[cat] = mutableListOf()
                        catMap[cat]?.add(keyName)

                        // Populate empty keys
                        if (!localTranslations.containsKey(keyName)) {
                            localTranslations[keyName] = ""
                        }
                        if (!cloudTranslations.containsKey(keyName)) {
                            cloudTranslations[keyName] = ""
                        }
                    }
                } catch (e: Exception) { }
            }
            
            categorizedStrings.clear()
            catMap.forEach { (cat, list) -> categorizedStrings[cat] = list }
            categories = catMap.keys.toList().sorted()
            
            withContext(Dispatchers.Main) {
                binding.tabCategories.removeAllTabs()
                categories.forEach { cat ->
                    binding.tabCategories.addTab(binding.tabCategories.newTab().setText(cat))
                }
                
                if (categories.isNotEmpty() && !categories.contains(selectedCategory)) {
                    selectedCategory = categories.first()
                }
                
                val index = categories.indexOf(selectedCategory)
                if (index >= 0 && index < binding.tabCategories.tabCount) {
                    binding.tabCategories.selectTab(binding.tabCategories.getTabAt(index))
                }

                // Update menu visibility
                val syncItem = binding.topAppBar.menu.findItem(R.id.action_sync)
                syncItem?.isVisible = (currentSource == SOURCE_CLOUD)
                
                updateList()
                calculateProgress()
                
                binding.progressBar.visibility = View.GONE
                binding.contentContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun updateList() {
        val keys = categorizedStrings[selectedCategory] ?: emptyList()
        adapter.updateKeys(keys)
    }

    private fun calculateProgress() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            if (selectedLangCode == "ar" || selectedLangCode == "en") {
                withContext(Dispatchers.Main) { 
                    binding.pbCompletion.progress = 100
                    binding.tvProgressLabel.text = AppStrings.get(requireContext(), R.string.string_translation_progress, 100)
                }
                return@launch
            }
            
            var translatedCount = 0
            val conf = android.content.res.Configuration(requireContext().resources.configuration)
            conf.setLocale(java.util.Locale(selectedLangCode))
            val localizedContext = requireContext().createConfigurationContext(conf)
            
            val activeMap = if (currentSource == SOURCE_LOCAL) localTranslations else cloudTranslations

            originalStrings.keys.forEach { key ->
                try {
                    val id = requireContext().resources.getIdentifier(key, "string", requireContext().packageName)
                    if (id != 0) {
                        val locStr = localizedContext.getString(id)
                        val fbStr = requireContext().getString(id) // Get default
                        if (locStr != fbStr && locStr.isNotBlank()) {
                            translatedCount++
                        } else if (activeMap[key]?.isNotBlank() == true) {
                            translatedCount++
                        }
                    }
                } catch (e: Exception) {}
            }
            val percent = if (originalStrings.isEmpty()) 0f else (translatedCount.toFloat() / originalStrings.size.toFloat()) * 100
            val pInt = percent.toInt()
            withContext(Dispatchers.Main) {
                binding.pbCompletion.progress = pInt
                binding.tvProgressLabel.text = AppStrings.get(requireContext(), R.string.string_translation_progress, pInt)
                if (pInt >= lastBeepPercent + 5) {
                    try {
                        com.example.accessiblevideoeditor.updater.BeepUtils.playProgressBeep(pInt)
                    } catch (_: Exception) {}
                    lastBeepPercent = pInt
                }
            }
        }
    }

    private fun updateProgressText() {
        // Quick update without deep recalculation
    }

    private fun saveAndApply() {
        try {
            val jsonLocal = JSONObject()
            localTranslations.forEach { (k, v) -> 
                if (v.isNotBlank()) {
                    jsonLocal.put(k, v) 
                }
            }
            val fileLocal = File(requireContext().filesDir, "local_lang_$selectedLangCode.json")
            fileLocal.writeText(jsonLocal.toString(4))

            val jsonCloud = JSONObject()
            cloudTranslations.forEach { (k, v) -> 
                if (v.isNotBlank()) {
                    jsonCloud.put(k, v) 
                }
            }
            val fileCloud = File(requireContext().filesDir, "cloud_lang_$selectedLangCode.json")
            fileCloud.writeText(jsonCloud.toString(4))

            AppStrings.loadCustomStrings(requireContext())
            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_256), Toast.LENGTH_SHORT).show()
            requireActivity().recreate()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_257), Toast.LENGTH_LONG).show()
        }
    }
    
    private fun syncFromLocal() {
        var syncCount = 0
        localTranslations.forEach { (k, v) ->
            if (v.isNotBlank() && cloudTranslations[k].isNullOrBlank()) {
                cloudTranslations[k] = v
                syncCount++
            }
        }

        if (syncCount > 0) {
            // Update active map reference if currently in cloud mode
            if (currentSource == SOURCE_CLOUD) {
                translations.clear()
                translations.putAll(cloudTranslations)
                adapter.updateData(
                    newIsCloudSource = true,
                    newTranslations = translations,
                    newLocalTranslations = localTranslations
                )
                updateList()
                calculateProgress()
            }
            Toast.makeText(
                requireContext(),
                AppStrings.get(requireContext(), R.string.msg_sync_suggestions_success, syncCount),
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                requireContext(),
                AppStrings.get(requireContext(), R.string.msg_no_suggestions_to_sync),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun getCategoryArabic(arText: String): String {
        val text = arText.lowercase()
        if (listOf("إعداد", "لغة", "مفتاح", "تطبيق", "لون", "شاشة").any { it in text }) return "إعدادات"
        if (listOf("قص", "استخراج", "صمت", "صوت", "فيديو", "معالجة", "تحديد", "تصدير", "دمج", "تحويل", "صورة").any { it in text }) return "أدوات ووسائط"
        if (listOf("تشغيل", "ايقاف", "إيقاف", "تقديم", "تأخير", "مدة", "وقت").any { it in text }) return "مشغل الفيديو"
        if (listOf("مساعدة", "حول", "تحديث", "مطور", "دليل", "بريد").any { it in text }) return "مساعدة وحول"
        if (listOf("خطأ", "فشل", "نجاح", "انتظر", "تحميل", "إلغاء", "موافق", "نعم", "لا", "حفظ", "يرجى").any { it in text }) return "حوارات عامة"
        return "نصوص عامة"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
