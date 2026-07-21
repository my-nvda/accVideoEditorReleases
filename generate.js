const fs = require('fs');
const path = require('path');

const screens = [
    'VideoTrimmer', 'SmartCut', 'MergeVideos', 'ReverseMedia', 
    'AudioEditor', 'AudioStudio', 'ExtractAudio', 'BoostVolume', 
    'CompressVideo', 'ImageEditor', 'Watermark', 'CreateBlankImage', 
    'SlideshowMaker', 'TickerText', 'AiAnalysis', 'Stt', 
    'Ocr', 'BatchProcess', 'FastConverter', 'History', 
    'Help', 'VolunteerTranslation'
];

const fragmentsDir = path.join(__dirname, 'app', 'src', 'main', 'java', 'com', 'example', 'accessiblevideoeditor', 'ui', 'fragments');
const layoutDir = path.join(__dirname, 'app', 'src', 'main', 'res', 'layout');

screens.forEach(screen => {
    // 1. Generate Fragment
    const fragmentName = `${screen}Fragment`;
    const fragmentCode = `package com.example.accessiblevideoeditor.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.databinding.Fragment${screen}Binding
import com.example.accessiblevideoeditor.R

class ${fragmentName} : Fragment() {

    private var _binding: Fragment${screen}Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Fragment${screen}Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
`;
    fs.writeFileSync(path.join(fragmentsDir, `${fragmentName}.kt`), fragmentCode);

    // 2. Generate XML Layout
    // Convert CamelCase to snake_case for layout name
    const layoutName = `fragment_${screen.replace(/([a-z])([A-Z])/g, '$1_$2').toLowerCase()}`;
    const xmlCode = `<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/topAppBar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        android:background="?attr/colorPrimary"
        app:title="${screen.replace(/([A-Z])/g, ' $1').trim()}"
        app:titleTextColor="?attr/colorOnPrimary"
        app:navigationIcon="@android:drawable/ic_menu_revert" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:fillViewport="true">
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">
            
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Work in progress: ${screen}" />
        </LinearLayout>
    </ScrollView>
</LinearLayout>`;
    fs.writeFileSync(path.join(layoutDir, `${layoutName}.xml`), xmlCode);
    
    console.log(`Generated ${fragmentName} and ${layoutName}`);
});

// 3. Generate Nav Graph fragments
let navGraph = '';
screens.forEach(screen => {
    const fragmentName = `${screen}Fragment`;
    const layoutName = `fragment_${screen.replace(/([a-z])([A-Z])/g, '$1_$2').toLowerCase()}`;
    const idName = fragmentName.charAt(0).toLowerCase() + fragmentName.slice(1);
    
    navGraph += `    <fragment\n        android:id="@+id/${idName}"\n        android:name="com.example.accessiblevideoeditor.ui.fragments.${fragmentName}"\n        android:label="${fragmentName}"\n        tools:layout="@layout/${layoutName}" />\n`;
});
fs.writeFileSync('nav_graph_snippets.txt', navGraph);

// 4. Generate Nav Graph Actions
let navActions = '';
screens.forEach(screen => {
    const idName = `${screen}Fragment`.charAt(0).toLowerCase() + `${screen}Fragment`.slice(1);
    navActions += `        <action\n            android:id="@+id/action_homeFragment_to_${idName}"\n            app:destination="@id/${idName}" />\n`;
});
fs.writeFileSync('nav_actions_snippets.txt', navActions);

// 5. Generate HomeFragment clicks
let homeClicks = '';
screens.forEach(screen => {
    const idName = `${screen}Fragment`.charAt(0).toLowerCase() + `${screen}Fragment`.slice(1);
    const btnName = `btn${screen}`;
    homeClicks += `        binding.${btnName}.setOnClickListener { findNavController().navigate(R.id.action_homeFragment_to_${idName}) }\n`;
});
fs.writeFileSync('home_clicks_snippets.txt', homeClicks);

console.log("Done generating files and snippets.");
