package com.example.accessiblevideoeditor.ui.main

import com.example.accessiblevideoeditor.data.TextOverlayConfig
import com.example.accessiblevideoeditor.data.UnifiedProjectModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedProjectModelTest {

    @Test
    fun testTextOverlayConfigInitialization() {
        val config = TextOverlayConfig(
            text = "Hello Test",
            startTimeMs = 1000,
            endTimeMs = 5000,
            animationType = "fade_in"
        )
        assertEquals("Hello Test", config.text)
        assertEquals(1000L, config.startTimeMs)
        assertEquals(5000L, config.endTimeMs)
        assertEquals("fade_in", config.animationType)
    }

    @Test
    fun testUnifiedProjectModelInitialization() {
        val project = UnifiedProjectModel(
            name = "Test Project",
            videoPath = "path/to/video.mp4"
        )
        project.textOverlays.add(
            TextOverlayConfig(
                text = "Overlay 1",
                startTimeMs = 0,
                endTimeMs = 2000
            )
        )
        assertEquals("Test Project", project.name)
        assertEquals("path/to/video.mp4", project.videoPath)
        assertEquals(1, project.textOverlays.size)
        assertEquals("Overlay 1", project.textOverlays[0].text)
    }
}
