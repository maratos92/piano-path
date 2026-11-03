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
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun aboutPanelShowsTheActualInstalledVersion() {
        compose.onNodeWithContentDescription("Settings",useUnmergedTree=true).performClick()
        compose.onNodeWithText("About Piano Path").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Revision ${BuildConfig.BUILD_REVISION}").performScrollTo().assertIsDisplayed()
    }
    @Test fun dashboardAndLibraryOpenOffline() {
        compose.onNodeWithText("Make room for music.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Library",useUnmergedTree=true).performClick()
        compose.onNodeWithText("The library").assertIsDisplayed()
        compose.onNodeWithText("Import MusicXML / MXL").assertIsDisplayed()
    }
    @Test fun androidXmlParserLoadsAllAssetsAndExercises() {
        val context=compose.activity
        val files=context.assets.list("scores")?.filter{it.endsWith("musicxml")} ?: emptyList()
        assertTrue("No bundled demo scores are expected",files.isEmpty())
        files.forEach{file->val xml=context.assets.open("scores/$file").bufferedReader().use{it.readText()};assertTrue(MusicXml.parse(xml).notes.isNotEmpty())}
        assertTrue(MusicXml.parse(Exercises.sight(level=3,seed=44).xml).notes.isNotEmpty())
        assertEquals(29,MusicXml.parse(Exercises.scale(octaves=2,hand=Hand.RIGHT).xml).notes.count{!it.rest})
    }
    @Test fun practiceRendersNotationInAndroidWebView() {
        compose.onNodeWithText("Open practice  →").performClick()
        compose.waitUntil(20_000){compose.onAllNodesWithText("Both").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Sheet").performClick()
        compose.waitForIdle()
        val rendered=AtomicBoolean(false);var diagnostic="WebView not found"
        fun find(view: android.view.View): WebView? {
            if(view is WebView)return view
            if(view is android.view.ViewGroup)for(i in 0 until view.childCount){val result=find(view.getChildAt(i));if(result!=null)return result}
            return null
        }
        try { compose.waitUntil(30_000){
            compose.runOnUiThread{find(compose.activity.window.decorView)?.also{diagnostic="WebView mounted; waiting for JavaScript"}?.evaluateJavascript("JSON.stringify({url:location.href,svg:!!document.querySelector('svg .vf-notehead'),error:document.getElementById('error')?.textContent,loading:document.getElementById('loading')?.textContent,engine:typeof opensheetmusicdisplay,html:document.body?.innerText?.slice(0,600)})"){value->diagnostic=value;val state=runCatching{org.json.JSONObject(org.json.JSONTokener(value).nextValue() as String)}.getOrNull();if(state?.let{it.optBoolean("svg") && it.optString("error").isBlank()}==true){rendered.set(true)}}}
            rendered.get()
        } } finally {println("WEBVIEW_STATE=$diagnostic")}
        assertTrue("Local MusicXML must render in Android WebView. Package=${WebView.getCurrentWebViewPackage()?.versionName}. State=$diagnostic",rendered.get())
    }
    @Test fun simulatedWaitSessionCompletesWithoutHardware() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            vm.setSettings{it.copy(simulator=true)}
            vm.scale(0,ScaleKind.MAJOR,1,Hand.BOTH,true)
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
        // The practice toolbar uses the short "Falling" label on narrow devices.
        // Wait for the score to load, then verify the view controls separately.
        compose.waitUntil(20_000){compose.onAllNodesWithText("Both").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Falling").assertIsDisplayed()
        compose.onNodeWithText("Both").assertIsDisplayed()
        compose.onNodeWithContentDescription("88-key piano, A0 to C8").assertIsDisplayed()
        compose.onNodeWithContentDescription("Falling notes aligned to the 88-key piano").assertIsDisplayed()
        compose.onNodeWithText("Fit all 88").assertIsDisplayed()
        compose.onNodeWithText("Listen").assertIsDisplayed()
    }
    @Test fun cursorSeekEndsScoredTakeAndMovesNextPracticeStart() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            vm.scale(0,ScaleKind.MAJOR,1,Hand.BOTH,true)
        }
        compose.waitUntil(15_000){vm.ui.value.score!=null}
        compose.runOnUiThread {
            vm.edit {it.copy(countIn=false,loop=false)}
            vm.start()
            assertTrue(vm.ui.value.running)
            vm.seek(2.0)
            assertFalse("Dragging a score cursor must end the graded take",vm.ui.value.running)
            assertEquals(2.0,vm.ui.value.transportBeat,0.01)
            val p=vm.ui.value
            val actualStart=p.score!!.measures[p.startMeasure-1].start+p.startOffset
            assertEquals(2.0,actualStart,0.01)
        }
    }
    @Test fun listeningAcceptsTouchKeysWithoutSimulatorAndReleasesAfterStop() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            vm.scale(0,ScaleKind.MAJOR,1,Hand.BOTH,true)
        }
        compose.waitUntil(15_000){vm.ui.value.score!=null}
        compose.runOnUiThread {
            vm.setSettings{it.copy(simulator=false)}
            vm.edit{it.copy(loop=false,bpm=70.0,endMeasure=1,endOffset=4.0)}
            vm.listen()
            assertTrue(vm.ui.value.listening)
            vm.key(60,true)
            assertTrue("Touch key must be held during Listen",60 in vm.midi.state.value.held)
            vm.stopListening()
            vm.key(60,false)
            assertTrue("Key-up after Listen stops must still reach the keyboard",vm.midi.state.value.held.isEmpty())
            assertNull(vm.ui.value.result)
        }
    }
    @Test fun listeningNeverProducesAnAccuracyResult() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java];vm.scale(0,ScaleKind.MAJOR,1,Hand.BOTH,true)}
        compose.waitUntil(15_000){vm.ui.value.score!=null}
        compose.runOnUiThread {vm.edit{it.copy(loop=false,bpm=240.0,endMeasure=1,endOffset=1.0)};vm.listen();assertTrue("Audio playback must start",vm.ui.value.listening)}
        compose.waitUntil(5_000){!vm.ui.value.listening}
        assertNull(vm.ui.value.result);assertTrue(vm.ui.value.loops.isEmpty());assertFalse(vm.ui.value.running)
    }
    @Test fun recordedTakeRoundTripsWithItsScoreAndPhysicalRelease() {
        val xml=Exercises.scale(octaves=1,hand=Hand.RIGHT).xml
        val score=MusicXml.parse(xml)
        val engine=PerformanceEngine(score,PracticeConfig(PracticeMode.RHYTHM,bpm=60.0,endBeat=4.0))
        val start=1_000_000_000L;engine.start(start)
        engine.accept(MidiEvent.On(score.notes.first{!it.rest}.midi,92,start))
        engine.accept(MidiEvent.Off(score.notes.first{!it.rest}.midi,0,start+250_000_000))
        engine.accept(MidiEvent.On(61,35,start+300_000_000))
        val result=engine.finish(start+4_000_000_000)
        val practice=PracticeUi(score=score,xml=xml,bpm=60.0,endOffset=4.0)
        val filename=org.pianopath.data.TakeArchive.save(compose.activity,practice,result)
        try {
            val session=org.pianopath.data.SessionEntity(title=score.title,date=0,pitch=result.pitch,rhythm=result.rhythm,overall=result.overall,
                correct=result.correct,expected=result.expected,extra=result.extra,missing=result.missing,onsetMs=result.meanOnsetMs,durationMs=result.meanDurationMs,
                mode="RHYTHM",bpm=60.0,category="Scales",simulated=true,reviewFile=filename)
            val loaded=org.pianopath.data.TakeArchive.load(compose.activity,session)
            assertEquals(xml,loaded.xml);assertEquals(result.take,loaded.take);assertTrue(loaded.review)
            assertEquals(.25,loaded.take.first().duration!!,.001);assertEquals(92,loaded.take.first().velocity)
            assertEquals(result.missing,loaded.result!!.missing)
        } finally {java.io.File(compose.activity.filesDir,"takes/$filename").delete()}
    }
    @Test fun landscapePracticeUsesExpandableNavigation() {
        compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        compose.runOnUiThread {ViewModelProvider(compose.activity)[PianoViewModel::class.java].scale(0,ScaleKind.MAJOR,1,Hand.BOTH,true)}
        compose.waitUntil(20_000){compose.onAllNodesWithContentDescription("Open navigation menu").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Open navigation menu").assertIsDisplayed().performClick()
        compose.onNodeWithText("Settings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Note monitor").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Live note monitor").assertIsDisplayed()
    }

    @Test fun voluntarySupportExplainsTheExternalProviderWithoutLaunchingPayment() {
        assertTrue(BuildConfig.SUPPORT_URL.startsWith("https://ko-fi.com/"))
        compose.onNodeWithContentDescription("Settings",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Support Piano Path").performScrollTo().performClick()
        compose.onNodeWithText("Open Ko-fi").assertIsDisplayed()
        compose.onNodeWithText("Maybe later").assertIsDisplayed().performClick()
    }

}
