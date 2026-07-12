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
    onTranslationChanged: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "النص الأصلي:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = "النص الأصلي" }
            )
            Text(
                text = originalText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .semantics { contentDescription = "النص الأصلي هو: $originalText" }
            )
            
            com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
                value = translation,
                onValueChange = { 
                    onTranslationChanged(keyName, it)
                },
                hint = "الترجمة",
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { 
                        contentDescription = "حقل إدخال لترجمة النص: $originalText" 
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
    var selectedCategory by remember { mutableStateOf("نصوص عامة") }
    var isLangMenuExpanded by remember { mutableStateOf(false) }
    
    // Original Strings Map (key -> original arabic text)
    val originalStrings = remember { mutableStateMapOf<String, String>() }
    // Categorized Strings Map (category -> list of keys)
    val categorizedStrings = remember { mutableStateMapOf<String, List<String>>() }
    // User Translations Map (key -> translation)
    val translations = remember { mutableStateMapOf<String, String>() }
    
    var categories by remember { mutableStateOf(listOf<String>()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load initial data
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
        
        allAppStringIds.forEach { id ->
            try {
                val keyName = context.resources.getResourceEntryName(id)
                val arText = context.getString(id) // Get default string which is Arabic
                originalStrings[keyName] = arText
                
                val cat = getCategoryArabic(arText)
                if (!catMap.containsKey(cat)) catMap[cat] = mutableListOf()
                catMap[cat]?.add(keyName)
                
                translations[keyName] = existingTranslations[keyName] ?: ""
            } catch (e: Exception) { }
        }
        
        catMap.forEach { (cat, list) -> categorizedStrings[cat] = list }
        categories = catMap.keys.toList().sorted()
        if (categories.isNotEmpty() && selectedCategory !in categories) {
            selectedCategory = categories.first()
        }
        isLoading = false
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
            Toast.makeText(context, "تم الحفظ والتطبيق بنجاح", Toast.LENGTH_SHORT).show()
            if (context is android.app.Activity) {
                context.recreate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "حدث خطأ أثناء الحفظ", Toast.LENGTH_LONG).show()
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
                Toast.makeText(context, "تم تصدير الترجمة بنجاح", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "فشل تصدير الترجمة", Toast.LENGTH_SHORT).show()
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
                        
                        Toast.makeText(context, "تم استيراد الترجمة وتطبيقها", Toast.LENGTH_SHORT).show()
                        if (context is android.app.Activity) {
                            context.recreate()
                        }
                    } else {
                        Toast.makeText(context, "ملف غير صالح. لا يحتوي على نصوص الترجمة.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "حدث خطأ أثناء الاستيراد", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ترجمة التطبيق") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "رجوع للخلف" }) {
                        Text("رجوع")
                    }
                },
                actions = {
                    Button(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                        Text("استيراد")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = saveAndApply) {
                        Text("حفظ وتطبيق")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = {
                        val fileName = "translations_${selectedLangCode}.json"
                        exportLauncher.launch(fileName)
                    }) {
                        Text("تصدير")
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
                    OutlinedButton(
                        onClick = { isLangMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "اختيار لغة الترجمة المستهدفة" }
                    ) {
                        val langName = SUPPORTED_LANGUAGES_AR.find { it.first == selectedLangCode }?.second ?: selectedLangCode
                        Text("ترجمة إلى: $langName ▼")
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
                                modifier = Modifier.semantics { contentDescription = "قسم $cat" }
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
                            }
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
