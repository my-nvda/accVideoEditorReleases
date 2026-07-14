package com.example.accessiblevideoeditor.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.*
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.ui.AppStrings
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val SUPPORTED_LANGUAGES_AR = listOf(
    "ar" to "العربية",
    "en" to "الإنجليزية",
    "fr" to "الفرنسية",
    "es" to "الإسبانية",
    "zh-CN" to "الصينية",
    "ru" to "الروسية",
    "ja" to "اليابانية",
    "iw" to "العبرية",
    "fa" to "الفارسية",
    "ur" to "الأردية",
    "tr" to "التركية"
)

fun getCategoryArabic(arText: String): String {
    val text = arText.lowercase()
    if (listOf("إعداد", "لغة", "مفتاح", "تطبيق", "لون", "شاشة").any { it in text }) return "إعدادات"
    if (listOf("قص", "استخراج", "صمت", "صوت", "فيديو", "معالجة", "تحديد", "تصدير", "دمج", "تحويل", "صورة").any { it in text }) return "أدوات ووسائط"
    if (listOf("تشغيل", "ايقاف", "إيقاف", "تقديم", "تأخير", "مدة", "وقت").any { it in text }) return "مشغل الفيديو"
    if (listOf("مساعدة", "حول", "تحديث", "مطور", "دليل", "بريد").any { it in text }) return "مساعدة وحول"
    if (listOf("خطأ", "فشل", "نجاح", "انتظر", "تحميل", "إلغاء", "موافق", "نعم", "لا", "حفظ", "يرجى").any { it in text }) return "حوارات عامة"
    return "نصوص عامة"
}

@Composable
fun TranslationItem(
    keyName: String,
    originalText: String,
    translation: String,
    onTranslationChanged: (String, String) -> Unit,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_252),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_252) }
            )
            Text(
                text = originalText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .semantics { contentDescription = AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_253).replace("%1\$s", originalText) }
            )
            
            com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
                value = translation,
                onValueChange = { 
                    onTranslationChanged(keyName, it)
                },
                hint = AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_255),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { 
                        contentDescription = AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_254).replace("%1\$s", originalText) 
                    }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerTranslationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var selectedLangCode by remember { mutableStateOf("en") }
    var selectedCategory by remember { mutableStateOf("") }
    var isLangMenuExpanded by remember { mutableStateOf(false) }
    
    val originalStrings = remember { mutableStateMapOf<String, String>() }
    val categorizedStrings = remember { mutableStateMapOf<String, List<String>>() }
    val translations = remember { mutableStateMapOf<String, String>() }
    
    var categories by remember { mutableStateOf(listOf<String>()) }
    var isLoading by remember { mutableStateOf(true) }

    var completionPercentage by remember { mutableStateOf(0f) }

    fun calculateProgress(langCode: String) {
        coroutineScope.launch(Dispatchers.IO) {
            if (langCode == "ar" || langCode == "en") {
                withContext(Dispatchers.Main) { completionPercentage = 1f }
                return@launch
            }
            var translatedCount = 0
            val conf = android.content.res.Configuration(context.resources.configuration)
            conf.setLocale(java.util.Locale(langCode))
            val localizedContext = context.createConfigurationContext(conf)
            
            originalStrings.keys.forEach { key ->
                try {
                    val id = context.resources.getIdentifier(key, "string", context.packageName)
                    if (id != 0) {
                        val locStr = localizedContext.getString(id)
                        val fbStr = context.getString(id) // Get default
                        if (locStr != fbStr && locStr.isNotBlank()) {
                            translatedCount++
                        }
                    }
                } catch (e: Exception) {}
            }
            val percent = if (originalStrings.isEmpty()) 0f else translatedCount.toFloat() / originalStrings.size.toFloat()
            withContext(Dispatchers.Main) {
                completionPercentage = percent
            }
        }
    }

    LaunchedEffect(Unit) {
        val file = File(context.filesDir, "custom_lang.json")
        val existingTranslations = mutableMapOf<String, String>()
        if (file.exists()) {
            try {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                for (key in json.keys()) {
                    existingTranslations[key] = json.getString(key)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        val catMap = mutableMapOf<String, MutableList<String>>()
        
        val fields = R.string::class.java.fields
        fields.forEach { field ->
            try {
                if (field.name.startsWith("string_")) {
                    val id = field.getInt(null)
                    val keyName = field.name
                    val arText = context.getString(id)
                    originalStrings[keyName] = arText
                    
                    val cat = getCategoryArabic(arText)
                    if (!catMap.containsKey(cat)) catMap[cat] = mutableListOf()
                    catMap[cat]?.add(keyName)
                    
                    translations[keyName] = existingTranslations[keyName] ?: ""
                }
            } catch (e: Exception) { }
        }
        
        catMap.forEach { (cat, list) -> categorizedStrings[cat] = list }
        categories = catMap.keys.toList().sorted()
        if (categories.isNotEmpty()) {
            selectedCategory = categories.first()
        }
        isLoading = false
        calculateProgress(selectedLangCode)
    }

    val saveAndApply: () -> Unit = {
        try {
            val json = JSONObject()
            translations.forEach { (k, v) -> 
                if (v.isNotBlank()) {
                    json.put(k, v) 
                }
            }
            val file = File(context.filesDir, "custom_lang.json")
            file.writeText(json.toString(4))
            AppStrings.loadCustomStrings(context)
            Toast.makeText(context, AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_256), Toast.LENGTH_SHORT).show()
            if (context is android.app.Activity) {
                context.recreate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_257), Toast.LENGTH_LONG).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = JSONObject()
                translations.forEach { (k, v) -> 
                    if (v.isNotBlank()) json.put(k, v) 
                }
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toString(4).toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_258), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_259), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
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
                        val file = File(context.filesDir, "custom_lang.json")
                        file.writeText(jsonString)
                        AppStrings.loadCustomStrings(context)
                        
                        val newKeys = json.keys()
                        while (newKeys.hasNext()) {
                            val k = newKeys.next()
                            translations[k] = json.getString(k)
                        }
                        
                        Toast.makeText(context, AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_260), Toast.LENGTH_SHORT).show()
                        if (context is android.app.Activity) {
                            context.recreate()
                        }
                    } else {
                        Toast.makeText(context, AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_261), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_262), Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_245)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_246) }) {
                        Text("<-")
                    }
                },
                actions = {
                    Button(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                        Text(AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_247))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = saveAndApply) {
                        Text(AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_248))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = {
                        val fileName = "translations_${selectedLangCode}.json"
                        exportLauncher.launch(fileName)
                    }) {
                        Text(AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_249))
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Language Selector
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column {
                        OutlinedButton(
                            onClick = { isLangMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_250) }
                        ) {
                            val langName = SUPPORTED_LANGUAGES_AR.find { it.first == selectedLangCode }?.second ?: selectedLangCode
                            Text(AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_251).replace("%1\$s", langName))
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Progress Bar
                        val progressPercent = (completionPercentage * 100).toInt()
                        Text(
                            text = "نسبة اكتمال الترجمة: $progressPercent%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = { completionPercentage },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = isLangMenuExpanded,
                        onDismissRequest = { isLangMenuExpanded = false }
                    ) {
                        SUPPORTED_LANGUAGES_AR.forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedLangCode = code
                                    isLangMenuExpanded = false
                                    calculateProgress(code)
                                }
                            )
                        }
                    }
                }

                // Categories TabRow
                if (categories.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = categories.indexOf(selectedCategory).takeIf { it >= 0 } ?: 0,
                        edgePadding = 8.dp
                    ) {
                        categories.forEach { cat ->
                            Tab(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                text = { Text(cat) },
                                modifier = Modifier.semantics { contentDescription = AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_263).replace("%1\$s", cat) }
                            )
                        }
                    }
                }

                // Strings List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val currentKeys = categorizedStrings[selectedCategory] ?: emptyList()
                    
                    items(currentKeys, key = { it }) { keyName ->
                        val originalText = originalStrings[keyName] ?: ""
                        val initialTrans = translations[keyName] ?: ""
                        
                        TranslationItem(
                            keyName = keyName,
                            originalText = originalText,
                            translation = initialTrans,
                            onTranslationChanged = { k, newText ->
                                translations[k] = newText
                            },
                            context = context
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
