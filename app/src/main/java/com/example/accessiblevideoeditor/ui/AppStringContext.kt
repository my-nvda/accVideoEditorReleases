package com.example.accessiblevideoeditor.ui

// This class has been intentionally removed.
// AppStringContext previously wrapped the Activity context and replaced its Resources with
// AppStringResources. This caused Material3 theme attribute resolution failures
// (colorContainer, colorOnPrimary, etc.) leading to InflateException crashes on all screens.
//
// Translation is now handled purely via AppStrings.get(context, resId) at the call site.
