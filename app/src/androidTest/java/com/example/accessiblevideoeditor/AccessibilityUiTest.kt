package com.example.accessiblevideoeditor

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import com.example.accessiblevideoeditor.ui.AppStrings

@RunWith(AndroidJUnit4::class)
class AccessibilityUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    companion object {
        @BeforeClass
        @JvmStatic
        fun enableAccessibilityChecks() {
            AccessibilityChecks.enable()
        }
    }

    @Test
    fun testAllScreensAccessibility() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        composeTestRule.waitForIdle()

        // List of all screen button string resource IDs on the Home Screen
        val screenTitleIds = listOf(
            R.string.string_112, // Video Editor
            R.string.string_128, // Image Editor
            R.string.string_74,  // Watermark
            R.string.string_271, // Create Blank Image
            R.string.string_94,  // Video Trimmer
            R.string.string_45,  // Smart Cut
            R.string.string_102, // Audio Editor
            R.string.string_55,  // Audio Studio
            R.string.string_31,  // AI Analysis
            R.string.string_63,  // STT
            R.string.string_20,  // OCR
            R.string.string_59,  // Fast Converter
            R.string.string_86,  // Boost Volume
            R.string.string_41,  // Extract Audio
            R.string.string_125, // Compress Video
            R.string.string_75,  // Merge Videos
            R.string.string_68,  // Reverse Media
            R.string.string_80,  // Slideshow Maker
            R.string.string_52,  // Ticker Text
            R.string.string_32,  // Batch Process
            R.string.string_116  // History
        )

        for (stringId in screenTitleIds) {
            // 1. Get the localized string dynamically
            val buttonText = AppStrings.get(context, stringId)

            // 2. Scroll to the button in the grid to ensure it's visible, then click it
            val node = composeTestRule.onNodeWithText(buttonText)
            
            // Check if node exists before performing actions
            node.assertExists("Button $buttonText not found on home screen")
            node.performScrollTo().performClick()

            // 3. Wait for the inner screen to render
            composeTestRule.waitForIdle()

            // At this point, the Espresso AccessibilityChecks and Compose Semantics
            // will automatically evaluate the inner screen hierarchy.

            // 4. Click the "Back" button to return to the Home Screen.
            // Assuming all inner screens have a localized "Back" or "Navigate up" content description.
            // Since some screens might use generic material back buttons, we look for typical back button semantics.
            val backButtonText = AppStrings.get(context, R.string.string_78) // "Back" translation
            
            val backNodeByContentDesc = composeTestRule.onAllNodesWithContentDescription(backButtonText, substring = true)
            val backNodeByText = composeTestRule.onAllNodesWithText(backButtonText, substring = true)
            
            if (backNodeByContentDesc.fetchSemanticsNodes().isNotEmpty()) {
                backNodeByContentDesc.onFirst().performClick()
            } else if (backNodeByText.fetchSemanticsNodes().isNotEmpty()) {
                backNodeByText.onFirst().performClick()
            } else {
                // If we can't find a software back button, perform a system back press
                androidx.test.espresso.Espresso.pressBack()
            }

            // 5. Wait for Home Screen to reload before testing the next inner screen
            composeTestRule.waitForIdle()
        }
    }
}
