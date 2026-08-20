package com.example.accessiblevideoeditor.ui

import android.content.Context
import android.net.Uri
import com.example.accessiblevideoeditor.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object ShareDialogHelper {
    
    fun showSuccessShareDialog(
        context: Context,
        savedUri: Uri?,
        defaultMessage: String,
        mimeType: String = "*/*",
        onDismiss: (() -> Unit)? = null
    ) {
        if (savedUri == null) {
            MaterialAlertDialogBuilder(context)
                .setTitle("نجاح العملية 🎉")
                .setMessage(defaultMessage)
                .setPositiveButton("إغلاق") { dialog, _ -> dialog.dismiss() }
                .setOnDismissListener { onDismiss?.invoke() }
                .show()
            return
        }
        
        val title = try { AppStrings.get(context, R.string.msg_success_dialog_title) } catch (_: Exception) { "عملية ناجحة 🎉" }
        val shareBtnText = "مشاركة الملف المخرّج 📤"
        val closeBtnText = try { AppStrings.get(context, R.string.btn_close) } catch (_: Exception) { "إغلاق" }
        
        MaterialAlertDialogBuilder(context)
            .setTitle(if (title.isNotBlank()) title else "عملية ناجحة 🎉")
            .setMessage("$defaultMessage\n\nهل تود مشاركة الملف المخرّج الآن؟")
            .setPositiveButton(shareBtnText) { dialog, _ ->
                dialog.dismiss()
                try {
                    val shareIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_STREAM, savedUri)
                        type = mimeType
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "مشاركة الملف المخرّج 📤"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton(closeBtnText) { dialog, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener {
                onDismiss?.invoke()
            }
            .show()
    }
}
