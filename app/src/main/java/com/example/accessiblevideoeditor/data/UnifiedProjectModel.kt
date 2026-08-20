package com.example.accessiblevideoeditor.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TextOverlayConfig(
    val id: String = UUID.randomUUID().toString(),
    var text: String,
    var startTimeMs: Long,
    var endTimeMs: Long,
    var xPosPercent: Float = 0.5f,
    var yPosPercent: Float = 0.8f,
    var colorHex: String = "#FFFFFF",
    var fontSize: Int = 24,
    var fontPath: String = "",
    var animationType: String = "none", // none, fade_in, slide_up, slide_down, slide_left, zoom_in, bounce_in, typewriter, mask_reveal
    var hasBackdrop: Boolean = true
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("startTimeMs", startTimeMs)
        put("endTimeMs", endTimeMs)
        put("xPosPercent", xPosPercent.toDouble())
        put("yPosPercent", yPosPercent.toDouble())
        put("colorHex", colorHex)
        put("fontSize", fontSize)
        put("fontPath", fontPath)
        put("animationType", animationType)
        put("hasBackdrop", hasBackdrop)
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): TextOverlayConfig {
            return TextOverlayConfig(
                id = obj.optString("id", UUID.randomUUID().toString()),
                text = obj.getString("text"),
                startTimeMs = obj.getLong("startTimeMs"),
                endTimeMs = obj.getLong("endTimeMs"),
                xPosPercent = obj.optDouble("xPosPercent", 0.5).toFloat(),
                yPosPercent = obj.optDouble("yPosPercent", 0.8).toFloat(),
                colorHex = obj.optString("colorHex", "#FFFFFF"),
                fontSize = obj.optInt("fontSize", 24),
                fontPath = obj.optString("fontPath", ""),
                animationType = obj.optString("animationType", "none"),
                hasBackdrop = obj.optBoolean("hasBackdrop", true)
            )
        }
    }
}

data class UnifiedProjectModel(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var videoPath: String,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var trimEnabled: Boolean = false,
    var trimStartMs: Long = 0L,
    var trimEndMs: Long = 0L,
    val textOverlays: MutableList<TextOverlayConfig> = mutableListOf(),
    var watermarkEnabled: Boolean = false,
    var watermarkImagePath: String = "",
    var watermarkPosition: String = "top_right",
    var watermarkScale: Float = 0.2f,
    var watermarkOpacity: Float = 0.8f,
    var speedMultiplier: Float = 1.0f,
    var volumeLevel: Float = 1.0f,
    // CapCut-style Accessibility Modules
    var backgroundRemovalEnabled: Boolean = false,
    var backgroundRemovalType: String = "auto_subject", // auto_subject, green_screen, blue_screen
    var keyframePreset: String = "none", // none, zoom_in_center, pan_left_to_right, pan_right_to_left, bounce_in
    var maskPreset: String = "none", // none, split_50_50, circle_center, top_bottom_cinematic
    var colorFilterPreset: String = "none", // none, warm_cinematic, cool_noir, vivid_hdr, vintage_sepia
    var brightness: Float = 0.0f, // -1.0 to 1.0
    var contrast: Float = 1.0f, // 0.0 to 2.0
    var saturation: Float = 1.0f, // 0.0 to 3.0
    var speedCurvePreset: String = "linear", // linear, hero_ramp, montage_pulse, bullet_time
    var stabilizationEnabled: Boolean = false,
    var backgroundRemovalCustomBgPath: String = "",
    var backgroundRemovalFpsOption: String = "auto"
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("videoPath", videoPath)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("trimEnabled", trimEnabled)
        put("trimStartMs", trimStartMs)
        put("trimEndMs", trimEndMs)
        
        val overlaysArray = JSONArray()
        textOverlays.forEach { overlaysArray.put(it.toJsonObject()) }
        put("textOverlays", overlaysArray)
        
        put("watermarkEnabled", watermarkEnabled)
        put("watermarkImagePath", watermarkImagePath)
        put("watermarkPosition", watermarkPosition)
        put("watermarkScale", watermarkScale.toDouble())
        put("watermarkOpacity", watermarkOpacity.toDouble())
        put("speedMultiplier", speedMultiplier.toDouble())
        put("volumeLevel", volumeLevel.toDouble())

        // Extended Accessibility Modules
        put("backgroundRemovalEnabled", backgroundRemovalEnabled)
        put("backgroundRemovalType", backgroundRemovalType)
        put("keyframePreset", keyframePreset)
        put("maskPreset", maskPreset)
        put("colorFilterPreset", colorFilterPreset)
        put("brightness", brightness.toDouble())
        put("contrast", contrast.toDouble())
        put("saturation", saturation.toDouble())
        put("speedCurvePreset", speedCurvePreset)
        put("stabilizationEnabled", stabilizationEnabled)
        put("backgroundRemovalCustomBgPath", backgroundRemovalCustomBgPath)
        put("backgroundRemovalFpsOption", backgroundRemovalFpsOption)
    }

    companion object {
        fun fromJsonString(jsonStr: String): UnifiedProjectModel {
            val obj = JSONObject(jsonStr)
            val proj = UnifiedProjectModel(
                id = obj.getString("id"),
                name = obj.getString("name"),
                videoPath = obj.getString("videoPath"),
                createdAt = obj.getLong("createdAt"),
                updatedAt = obj.optLong("updatedAt", obj.getLong("createdAt")),
                trimEnabled = obj.optBoolean("trimEnabled", false),
                trimStartMs = obj.optLong("trimStartMs", 0L),
                trimEndMs = obj.optLong("trimEndMs", 0L),
                watermarkEnabled = obj.optBoolean("watermarkEnabled", false),
                watermarkImagePath = obj.optString("watermarkImagePath", ""),
                watermarkPosition = obj.optString("watermarkPosition", "top_right"),
                watermarkScale = obj.optDouble("watermarkScale", 0.2).toFloat(),
                watermarkOpacity = obj.optDouble("watermarkOpacity", 0.8).toFloat(),
                speedMultiplier = obj.optDouble("speedMultiplier", 1.0).toFloat(),
                volumeLevel = obj.optDouble("volumeLevel", 1.0).toFloat(),
                backgroundRemovalEnabled = obj.optBoolean("backgroundRemovalEnabled", false),
                backgroundRemovalType = obj.optString("backgroundRemovalType", "auto_subject"),
                keyframePreset = obj.optString("keyframePreset", "none"),
                maskPreset = obj.optString("maskPreset", "none"),
                colorFilterPreset = obj.optString("colorFilterPreset", "none"),
                brightness = obj.optDouble("brightness", 0.0).toFloat(),
                contrast = obj.optDouble("contrast", 1.0).toFloat(),
                saturation = obj.optDouble("saturation", 1.0).toFloat(),
                speedCurvePreset = obj.optString("speedCurvePreset", "linear"),
                stabilizationEnabled = obj.optBoolean("stabilizationEnabled", false),
                backgroundRemovalCustomBgPath = obj.optString("backgroundRemovalCustomBgPath", ""),
                backgroundRemovalFpsOption = obj.optString("backgroundRemovalFpsOption", "auto")
            )
            
            val overlaysArray = obj.optJSONArray("textOverlays")
            if (overlaysArray != null) {
                for (i in 0 until overlaysArray.length()) {
                    proj.textOverlays.add(TextOverlayConfig.fromJsonObject(overlaysArray.getJSONObject(i)))
                }
            }
            return proj
        }
    }
}
