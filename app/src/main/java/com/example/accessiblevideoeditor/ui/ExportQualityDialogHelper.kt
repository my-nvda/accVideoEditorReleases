package com.example.accessiblevideoeditor.ui

import android.content.Context
import com.example.accessiblevideoeditor.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.CheckBox

object ExportQualityDialogHelper {

    fun showQualityDialog(
        context: Context,
        onSelected: (String) -> Unit
    ) {
        if (!SettingsManager.shouldAskExportQuality) {
            onSelected(SettingsManager.exportQuality)
            return
        }

        val qualities = arrayOf("high", "medium", "low")
        val qualityNames = arrayOf(
            AppStrings.get(context, R.string.quality_high),
            AppStrings.get(context, R.string.quality_medium),
            AppStrings.get(context, R.string.quality_low)
        )

        var selectedIndex = qualities.indexOf(SettingsManager.exportQuality)
        if (selectedIndex < 0) selectedIndex = 0

        val checkBox = CheckBox(context).apply {
            text = "لا تسألني مرة أخرى"
            setPadding(32, 16, 32, 16)
        }

        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16)
            addView(checkBox)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("اختر جودة التصدير")
            .setSingleChoiceItems(qualityNames, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setView(container)
            .setPositiveButton("تصدير") { dialog, _ ->
                val chosenQuality = qualities[selectedIndex]
                SettingsManager.exportQuality = chosenQuality
                if (checkBox.isChecked) {
                    SettingsManager.shouldAskExportQuality = false
                }
                dialog.dismiss()
                onSelected(chosenQuality)
            }
            .setNegativeButton("إلغاء") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
