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
    "ar" to "ط§ظ„ط¹ط±ط¨ظٹط©",
    "en" to "ط§ظ„ط¥ظ†ط¬ظ„ظٹط²ظٹط©",
    "fr" to "ط§ظ„ظپط±ظ†ط³ظٹط©",
    "es" to "ط§ظ„ط¥ط³ط¨ط§ظ†ظٹط©",
    "zh-CN" to "ط§ظ„طµظٹظ†ظٹط©",
    "ru" to "ط§ظ„ط±ظˆط³ظٹط©",
    "ja" to "ط§ظ„ظٹط§ط¨ط§ظ†ظٹط©",
    "iw" to "ط§ظ„ط¹ط¨ط±ظٹط©",
    "fa" to "ط§ظ„ظپط§ط±ط³ظٹط©",
    "ur" to "ط§ظ„ط£ط±ط¯ظٹط©",
    "tr" to "ط§ظ„طھط±ظƒظٹط©"
)

fun getCategoryArabic(arText: String): String {
    val text = arText.lowercase()
    if (listOf("ط¥ط¹ط¯ط§ط¯", "ظ„ط؛ط©", "ظ…ظپطھط§ط­", "طھط·ط¨ظٹظ‚", "ظ„ظˆظ†", "ط´ط§ط´ط©").any { it in text }) return "ط¥ط¹ط¯ط§ط¯ط§طھ"
    if (listOf("ظ‚طµ", "ط§ط³طھط®ط±ط§ط¬", "طµظ…طھ", "طµظˆطھ", "ظپظٹط¯ظٹظˆ", "ظ…ط¹ط§ظ„ط¬ط©", "طھط­ط¯ظٹط¯", "طھطµط¯ظٹط±", "ط¯ظ…ط¬", "طھط­ظˆظٹظ„", "طµظˆط±ط©").any { it in text }) return "ط£ط¯ظˆط§طھ ظˆظˆط³ط§ط¦ط·"
    if (listOf("طھط´ط؛ظٹظ„", "ط§ظٹظ‚ط§ظپ", "ط¥ظٹظ‚ط§ظپ", "طھظ‚ط¯ظٹظ…", "طھط£ط®ظٹط±", "ظ…ط¯ط©", "ظˆظ‚طھ").any { it in text }) return "ظ…ط´ط؛ظ„ ط§ظ„ظپظٹط¯ظٹظˆ"
    if (listOf("ظ…ط³ط§ط¹ط¯ط©", "ط­ظˆظ„", "طھط­ط¯ظٹط«", "ظ…ط·ظˆط±", "ط¯ظ„ظٹظ„", "ط¨ط±ظٹط¯").any { it in text }) return "ظ…ط³ط§ط¹ط¯ط© ظˆط­ظˆظ„"
    if (listOf("ط®ط·ط£", "ظپط´ظ„", "ظ†ط¬ط§ط­", "ط§ظ†طھط¸ط±", "طھط­ظ…ظٹظ„", "ط¥ظ„ط؛ط§ط،", "ظ…ظˆط§ظپظ‚", "ظ†ط¹ظ…", "ظ„ط§", "ط­ظپط¸", "ظٹط±ط¬ظ‰").any { it in text }) return "ط­ظˆط§ط±ط§طھ ط¹ط§ظ…ط©"
    return "ظ†طµظˆطµ ط¹ط§ظ…ط©"
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
                text = "ط§ظ„ظ†طµ ط§ظ„ط£طµظ„ظٹ:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = "ط§ظ„ظ†طµ ط§ظ„ط£طµظ„ظٹ" }
            )
            Text(
                text = originalText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .semantics { contentDescription = "ط§ظ„ظ†طµ ط§ظ„ط£طµظ„ظٹ ظ‡ظˆ: $originalText" }
            )
            
            com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
                value = translation,
                onValueChange = { 
                    onTranslationChanged(keyName, it)
                },
                hint = "ط§ظ„طھط±ط¬ظ…ط©",
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { 
                        contentDescription = "ط­ظ‚ظ„ ط¥ط¯ط®ط§ظ„ ظ„طھط±ط¬ظ…ط© ط§ظ„ظ†طµ: $originalText" 
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
    var selectedCategory by remember { mutableStateOf("ظ†طµظˆطµ ط¹ط§ظ…ط©") }
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
            Toast.makeText(context, "طھظ… ط§ظ„ط­ظپط¸ ظˆط§ظ„طھط·ط¨ظٹظ‚ ط¨ظ†ط¬ط§ط­", Toast.LENGTH_SHORT).show()
            if (context is android.app.Activity) {
                context.recreate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "ط­ط¯ط« ط®ط·ط£ ط£ط«ظ†ط§ط، ط§ظ„ط­ظپط¸", Toast.LENGTH_LONG).show()
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
                Toast.makeText(context, "طھظ… طھطµط¯ظٹط± ط§ظ„طھط±ط¬ظ…ط© ط¨ظ†ط¬ط§ط­", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "ظپط´ظ„ طھطµط¯ظٹط± ط§ظ„طھط±ط¬ظ…ط©", Toast.LENGTH_SHORT).show()
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
                        
                        Toast.makeText(context, "طھظ… ط§ط³طھظٹط±ط§ط¯ ط§ظ„طھط±ط¬ظ…ط© ظˆطھط·ط¨ظٹظ‚ظ‡ط§", Toast.LENGTH_SHORT).show()
                        if (context is android.app.Activity) {
                            context.recreate()
                        }
                    } else {
                        Toast.makeText(context, "ظ…ظ„ظپ ط؛ظٹط± طµط§ظ„ط­. ظ„ط§ ظٹط­طھظˆظٹ ط¹ظ„ظ‰ ظ†طµظˆطµ ط§ظ„طھط±ط¬ظ…ط©.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "ط­ط¯ط« ط®ط·ط£ ط£ط«ظ†ط§ط، ط§ظ„ط§ط³طھظٹط±ط§ط¯", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("طھط±ط¬ظ…ط© ط§ظ„طھط·ط¨ظٹظ‚") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "ط±ط¬ظˆط¹ ظ„ظ„ط®ظ„ظپ" }) {
                        Text("ط±ط¬ظˆط¹")
                    }
                },
                actions = {
                    Button(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                        Text("ط§ط³طھظٹط±ط§ط¯")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = saveAndApply) {
                        Text("ط­ظپط¸ ظˆطھط·ط¨ظٹظ‚")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = {
                        val fileName = "translations_${selectedLangCode}.json"
                        exportLauncher.launch(fileName)
                    }) {
                        Text("طھطµط¯ظٹط±")
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
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "ط§ط®طھظٹط§ط± ظ„ط؛ط© ط§ظ„طھط±ط¬ظ…ط© ط§ظ„ظ…ط³طھظ‡ط¯ظپط©" }
                    ) {
                        val langName = SUPPORTED_LANGUAGES_AR.find { it.first == selectedLangCode }?.second ?: selectedLangCode
                        Text("طھط±ط¬ظ…ط© ط¥ظ„ظ‰: $langName â–¼")
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
                                modifier = Modifier.semantics { contentDescription = "ظ‚ط³ظ… $cat" }
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

