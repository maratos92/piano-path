package org.pianopath

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pianopath.core.Hand
import org.pianopath.core.ScaleKind

@RunWith(AndroidJUnit4::class)
class PracticeControlsRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun openPractice(sight: Boolean = false): PianoViewModel {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            if(sight) vm.sight(1,0,4,Hand.RIGHT,0,120.0)
            else vm.scale(0,ScaleKind.MAJOR,1,Hand.RIGHT,true)
        }
        compose.waitUntil(15_000) { vm.ui.value.score != null && vm.screen.value == "Practice" }
        compose.onNodeWithContentDescription("Practice controls").performClick()
        return vm
    }

    @Test fun exerciseSettingsReturnToScaleSetup() {
        val vm=openPractice()
        compose.onNode(hasText("Exercise settings") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5_000) { vm.screen.value == "Scales" }
        compose.onNodeWithText("Scales & technique").assertIsDisplayed()
        compose.onNodeWithTag("start-scale").assertExists()
    }

    @Test fun exerciseSettingsReturnToSightReadingSetup() {
        val vm=openPractice(sight=true)
        compose.onNode(hasText("Exercise settings") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5_000) { vm.screen.value == "Sight reading" }
        compose.onNodeWithText("Difficulty").assertIsDisplayed()
        compose.onNodeWithTag("start-sight-reading").assertExists()
    }

    private fun startRemainsVisible(orientation: Int, configuration: Int) {
        compose.runOnUiThread { compose.activity.requestedOrientation=orientation }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == configuration }
        openPractice()
        for(tab in listOf("Practice", "Loop", "Score")) {
            compose.onNode(hasText(tab) and hasAnyAncestor(isDialog())).performClick()
            // Deliberately do not scroll: the action must stay visible on every page.
            compose.onNode(hasTestTag("start-practice") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        }
    }

    @Test fun practiceSettingsStartIsVisibleInPortrait() =
        startRemainsVisible(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,Configuration.ORIENTATION_PORTRAIT)

    @Test fun practiceSettingsStartIsVisibleInLandscape() =
        startRemainsVisible(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,Configuration.ORIENTATION_LANDSCAPE)

    private fun hardwareBackReturnsToSetup(screen: String, startTag: String, setupLabel: String) {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            vm.screen.value=screen
        }
        compose.onNodeWithTag(startTag).performScrollTo().performClick()
        compose.waitUntil(15_000) { vm.screen.value == "Practice" && vm.ui.value.score != null }
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { vm.screen.value == screen }
        compose.onNodeWithText(setupLabel).performScrollTo().assertIsDisplayed()
    }

    @Test fun hardwareBackReturnsToScaleSetup() =
        hardwareBackReturnsToSetup("Scales","start-scale","Scales & technique")

    @Test fun hardwareBackReturnsToSightReadingSetup() =
        hardwareBackReturnsToSetup("Sight reading","start-sight-reading","Difficulty")
}
