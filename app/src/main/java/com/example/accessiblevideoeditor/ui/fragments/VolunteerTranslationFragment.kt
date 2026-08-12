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
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class VolunteerTranslationFragment : Fragment() {

    private var _binding: FragmentVolunteerTranslationBinding? = null
    private val binding get() = _binding!!

    private var selectedLangCode = "en"
    private var selectedCategory = ""
    
    private val originalStrings = mutableMapOf<String, String>()
    private val categorizedStrings = mutableMapOf<String, List<String>>()
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
                        val file = File(requireContext().filesDir, "custom_lang_$selectedLangCode.json")
                        file.writeText(jsonString)
                        AppStrings.loadCustomStrings(requireContext())
                        
                        val newKeys = json.keys()
                        while (newKeys.hasNext()) {
                            val k = newKeys.next()
                            translations[k] = json.getString(k)
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
                    val fileName = "translations_${selectedLangCode}.json"
                    exportLauncher.launch(fileName)
                    true
                }
                else -> false
            }
        }

        setupLanguageSpinner()
        
        binding.rvTranslations.layoutManager = LinearLayoutManager(requireContext())
        adapter = TranslationAdapter(
            requireContext(),
            emptyList(),
            originalStrings,
            translations
        ) { key, newText ->
            translations[key] = newText
            updateProgressText() // optionally update without full recalc
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
            val file = File(requireContext().filesDir, "custom_lang_$selectedLangCode.json")
            val existingTranslations = mutableMapOf<String, String>()
            if (file.exists()) {
                try {
                    val json = JSONObject(file.readText(Charsets.UTF_8))
                    for (key in json.keys()) {
                        existingTranslations[key] = json.getString(key)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            translations.clear()
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
                        
                        translations[keyName] = existingTranslations[keyName] ?: ""
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
            
            originalStrings.keys.forEach { key ->
                try {
                    val id = requireContext().resources.getIdentifier(key, "string", requireContext().packageName)
                    if (id != 0) {
                        val locStr = localizedContext.getString(id)
                        val fbStr = requireContext().getString(id) // Get default
                        if (locStr != fbStr && locStr.isNotBlank()) {
                            translatedCount++
                        } else if (translations[key]?.isNotBlank() == true) {
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
        // Quick update without deep recalculation if needed, or just let it be.
    }

    private fun saveAndApply() {
        try {
            val json = JSONObject()
            translations.forEach { (k, v) -> 
                if (v.isNotBlank()) {
                    json.put(k, v) 
                }
            }
            val file = File(requireContext().filesDir, "custom_lang_$selectedLangCode.json")
            file.writeText(json.toString(4))
            AppStrings.loadCustomStrings(requireContext())
            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_256), Toast.LENGTH_SHORT).show()
            requireActivity().recreate()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_257), Toast.LENGTH_LONG).show()
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

