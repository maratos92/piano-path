package org.pianopath

import android.webkit.WebView
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.pianopath.core.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun dashboardAndLibraryOpenOffline() {
        compose.onNodeWithText("Make room for music.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Library",useUnmergedTree=true).performClick()
        compose.onNodeWithText("The library").assertIsDisplayed()
        compose.onNodeWithText("Import MusicXML / MXL").assertIsDisplayed()
    }
    @Test fun androidXmlParserLoadsAllAssetsAndExercises() {
        val context=compose.activity
        val files=context.assets.list("scores")!!.filter{it.endsWith("musicxml")}
        assertEquals(50,files.size)
        files.forEach{file->val xml=context.assets.open("scores/$file").bufferedReader().use{it.readText()};assertTrue(MusicXml.parse(xml).notes.isNotEmpty())}
        assertTrue(MusicXml.parse(Exercises.sight(level=3,seed=44).xml).notes.isNotEmpty())
        assertEquals(29,MusicXml.parse(Exercises.scale(octaves=2,hand=Hand.RIGHT).xml).notes.count{!it.rest})
    }
    @Test fun practiceRendersNotationInAndroidWebView() {
        compose.onNodeWithText("Open practice  →").performClick()
        compose.waitUntil(20_000){compose.onAllNodesWithText("First Light").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Sheet").performClick()
        val ready=CountDownLatch(1);var rendered=false
        fun find(view: android.view.View): WebView? {
            if(view is WebView)return view
            if(view is android.view.ViewGroup)for(i in 0 until view.childCount){val result=find(view.getChildAt(i));if(result!=null)return result}
            return null
        }
        repeat(30){
            compose.runOnUiThread{find(compose.activity.window.decorView)?.evaluateJavascript("Boolean(typeof ready !== 'undefined' && ready && document.querySelector('svg'))"){value->if(value=="true"){rendered=true;ready.countDown()}}}
            if(ready.await(1,TimeUnit.SECONDS))return@repeat
        }
        assertTrue("Local MusicXML must render in Android WebView",rendered)
    }
    @Test fun simulatedWaitSessionCompletesWithoutHardware() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            vm.setSettings{it.copy(simulator=true)}
            vm.open(vm.library.first())
        }
        compose.waitUntil(15_000){vm.ui.value.score!=null}
        compose.runOnUiThread {
            vm.edit{it.copy(mode=PracticeMode.WAIT,countIn=false,loop=false,endMeasure=1,endOffset=4.0)}
            vm.start()
        }
        repeat(4) {
            compose.runOnUiThread{vm.simulateNext()}
            compose.waitUntil(3_000){vm.midi.state.value.held.isEmpty()}
            Thread.sleep(450)
        }
        compose.waitUntil(10_000){!vm.ui.value.running}
        val result=vm.ui.value.result
        assertNotNull(result);assertEquals(100.0,result!!.pitch,0.01);assertNull(result.rhythm)
        assertTrue(vm.ui.value.simulated)
    }
    @Test fun fullKeyboardAndFallingNotesAreDiscoverable() {
        compose.onNodeWithText("Open practice  →").performClick()
        compose.onNodeWithText("Falling notes").assertIsDisplayed()
        compose.onNodeWithContentDescription("88-key piano, A0 to C8").assertIsDisplayed()
        compose.onNodeWithContentDescription("Falling notes aligned to the 88-key piano").assertIsDisplayed()
        compose.onNodeWithText("Fit all 88").assertIsDisplayed()
        compose.onNodeWithText("Listen").assertIsDisplayed()
    }
    @Test fun listeningNeverProducesAnAccuracyResult() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java];vm.open(vm.library.first())}
        compose.waitUntil(15_000){vm.ui.value.score!=null}
        compose.runOnUiThread {vm.edit{it.copy(loop=false,bpm=240.0,endMeasure=1,endOffset=1.0)};vm.listen();assertTrue("Audio playback must start",vm.ui.value.listening)}
        compose.waitUntil(5_000){!vm.ui.value.listening}
        assertNull(vm.ui.value.result);assertTrue(vm.ui.value.loops.isEmpty());assertFalse(vm.ui.value.running)
    }
}
