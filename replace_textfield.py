import os
import re

base_dir = r"app\src\main\java\com\example\accessiblevideoeditor\ui\screens"

files_to_check = [f for f in os.listdir(base_dir) if f.endswith(".kt")]

for filename in files_to_check:
    filepath = os.path.join(base_dir, filename)
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    # We need to replace OutlinedTextField with AccessibleTextField
    # Since regex can't parse nested parentheses well, I will just replace known patterns
    
    # We'll use a regex that matches OutlinedTextField( value = ..., onValueChange = ..., label = { Text("...") }, ... )
    
    # Actually, let's just do it file by file with simple string replacements
    if "OutlinedTextField" in content:
        print(f"Modifying {filename}")
        # Add import if needed
        if "AccessibleTextField" not in content:
            content = content.replace("import androidx.compose.material3.*", "import androidx.compose.material3.*\nimport com.example.accessiblevideoeditor.ui.components.AccessibleTextField")
        
        # Replace simple OutlinedTextField blocks
        # 1. AiAnalysisScreen
        content = content.replace('''OutlinedTextField(
            value = userInput,
            onValueChange = { userInput = it },
            label = { Text("«ÿ—Õ ”ƒ«·« ≈÷«›Ì« √Ê ÿ·» Ê’› „Œ’’...") },
            modifier = Modifier.fillMaxWidth()
        )''', '''AccessibleTextField(
            value = userInput,
            onValueChange = { userInput = it },
            hint = "«ÿ—Õ ”ƒ«·« ≈÷«›Ì« √Ê ÿ·» Ê’› „Œ’’...",
            modifier = Modifier.fillMaxWidth()
        )''')
        
        # 2. SmartCutScreen
        content = content.replace('''OutlinedTextField(
            value = silenceThreshold,
            onValueChange = { silenceThreshold = it },
            label = { Text("„” ÊÏ «·’„  („À«·: -30dB)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done)
        )''', '''AccessibleTextField(
            value = silenceThreshold,
            onValueChange = { silenceThreshold = it },
            hint = "„” ÊÏ «·’„  („À«·: -30dB)",
            modifier = Modifier.fillMaxWidth()
        )''')
        
        # 3. SlideshowMakerScreen
        content = content.replace('''OutlinedTextField(
            value = durationPerImage,
            onValueChange = { durationPerImage = it },
            label = { Text("„œ… ⁄—÷ «·’Ê—… (»«·ÀÊ«‰Ì)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
        )''', '''AccessibleTextField(
            value = durationPerImage,
            onValueChange = { durationPerImage = it },
            hint = "„œ… ⁄—÷ «·’Ê—… (»«·ÀÊ«‰Ì)",
            modifier = Modifier.fillMaxWidth()
        )''')
        
        # 4. TickerTextScreen
        content = content.replace('''OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("«·‰’ «·„ Õ—ﬂ") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )''', '''AccessibleTextField(
            value = text,
            onValueChange = { text = it },
            hint = "«·‰’ «·„ Õ—ﬂ",
            modifier = Modifier.fillMaxWidth()
        )''')
        
        # 5. OcrScreen
        content = content.replace('''OutlinedTextField(
                value = extractedText,
                onValueChange = {},
                label = { Text("«·‰’ «·„” Œ—Ã") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                readOnly = true
            )''', '''AccessibleTextField(
                value = extractedText,
                onValueChange = {},
                hint = "«·‰’ «·„” Œ—Ã",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                readOnly = true,
                minLines = 5
            )''')
        
        # 6. SpeechToTextScreen
        content = content.replace('''OutlinedTextField(
                value = transcribedText,
                onValueChange = {},
                label = { Text("«·‰’ «·„” Œ—Ã") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                readOnly = true
            )''', '''AccessibleTextField(
                value = transcribedText,
                onValueChange = {},
                hint = "«·‰’ «·„” Œ—Ã",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                readOnly = true,
                minLines = 5
            )''')
            
        # Write back
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)

print("Done replacing.")
