package com.example.accessiblevideoeditor.ui

// This class has been intentionally removed.
// TranslationInflaterFactory previously used reflection to set LayoutInflater.mFactory2,
// which bypassed AppCompatViewInflater and broke Material3 theme attribute resolution,
// causing InflateException crashes whenever any fragment or dialog was opened.
//
// Translation is now handled purely via AppStrings.get(context, resId) at the call site.
