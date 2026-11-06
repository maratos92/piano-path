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
        compose.onNodeWithText("About").performClick()
        compose.onNodeWithText("About Piano Path").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Revision ${BuildConfig.BUILD_REVISION}").performScrollTo().assertIsDisplayed()
    }
    @Test fun dashboardAndLibraryOpenOffline() {
        compose.onNode(hasText("Home") and hasAnyAncestor(hasTestTag("home-feed"))).assertIsDisplayed()
        compose.onNodeWithContentDescription("Library",useUnmergedTree=true).performClick()
        compose.onNode(hasText("Library") and hasAnyAncestor(hasTestTag("library-feed"))).assertIsDisplayed()
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
    @Test fun zoomedHorizontalScoreScrollsWithoutVerticalOscillation() {
        compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            vm.scale(0,ScaleKind.MAJOR,2,Hand.BOTH,true)
        }
        compose.waitUntil(20_000){vm.ui.value.score!=null}
        compose.runOnUiThread {vm.edit{it.copy(view=ScoreView.SHEET,horizontal=true,zoom=1.6)}}
        compose.waitForIdle()
        fun find(view: android.view.View): WebView? {
            if(view is WebView)return view
            if(view is android.view.ViewGroup)for(i in 0 until view.childCount){val result=find(view.getChildAt(i));if(result!=null)return result}
            return null
        }
        val rendered=AtomicBoolean(false)
        compose.waitUntil(30_000){
            compose.runOnUiThread {find(compose.activity.window.decorView)?.evaluateJavascript("typeof ready !== 'undefined' && ready && entries.length > 0"){rendered.set(it=="true")}}
            rendered.get()
        }
        // Use the renderer's real transport path, allowing a painted frame between
        // samples. Changing note ranges must not change the vertical scroll target.
        val script="""
            (() => {
              window.scrollCheck={running:true};
              (async () => {
                try {
                  const end=Math.max(...entries.map(e=>e.end));
                  const samples=[];
                  for(let beat=0;beat<end;beat+=.5){
                    progress(beat,[],[],0,end,1.6,true,[],[],180,4,false,80,"BOTH",true,true,true,end,true);
                    await new Promise(requestAnimationFrame);
                    samples.push({x:scrollRoot.scrollLeft,y:scrollRoot.scrollTop,height:parseFloat(document.getElementById('playhead').style.height)});
                  }
                  const spread=key=>Math.max(...samples.map(s=>s[key]))-Math.min(...samples.map(s=>s[key]));
                  window.scrollCheck={done:true,count:samples.length,measures:new Set(entries.map(e=>e.measure)).size,
                    ySpread:spread('y'),heightSpread:spread('height'),xTravel:spread('x'),
                    backwards:samples.slice(1).filter((s,i)=>s.x<samples[i].x-1).length,
                    viewport:{width:innerWidth,height:innerHeight,visualWidth:visualViewport?.width,documentWidth:document.scrollingElement.scrollWidth}};
                }catch(e){window.scrollCheck={done:true,error:String(e)}}
              })();
            })()
        """.trimIndent()
        compose.runOnUiThread {find(compose.activity.window.decorView)!!.evaluateJavascript(script,null)}
        val complete=AtomicBoolean(false);var diagnostic="No scroll result"
        var result: org.json.JSONObject?=null
        try {compose.waitUntil(30_000){
            compose.runOnUiThread {find(compose.activity.window.decorView)?.evaluateJavascript("JSON.stringify(window.scrollCheck)"){value->
                diagnostic=value
                val parsed=runCatching{org.json.JSONObject(org.json.JSONTokener(value).nextValue() as String)}.getOrNull()
                if(parsed?.optBoolean("done")==true){result=parsed;complete.set(true)}
            }}
            complete.get()
        }} finally {println("SCROLL_STATE=$diagnostic")}
        val state=requireNotNull(result)
        assertFalse("Renderer failed: $diagnostic",state.has("error"))
        assertTrue("Exercise must span multiple measures: $diagnostic",state.getInt("measures")>=4)
        assertTrue("Cursor must use a stable system height: $diagnostic",state.getDouble("heightSpread")<=1.0)
        assertTrue("Score must not bounce vertically between measures: $diagnostic",state.getDouble("ySpread")<=1.0)
        assertTrue("Test must actually scroll horizontally: $diagnostic",state.getDouble("xTravel")>20.0)
        assertEquals("Forward playback must not scroll backwards: $diagnostic",0,state.getInt("backwards"))
    }
    @Test fun fullKeyboardAndFallingNotesAreDiscoverable() {
        compose.onNodeWithText("Open practice  →").performClick()
        // The practice toolbar uses the short "Falling" label on narrow devices.
        // Wait for the score to load, then verify the view controls separately.
        compose.waitUntil(20_000){compose.onAllNodesWithText("Both").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Falling").assertIsDisplayed().performClick()
        compose.onNodeWithText("Both").assertIsDisplayed()
        compose.onNodeWithContentDescription("88-key piano, A0 to C8").assertIsDisplayed()
        compose.onNodeWithContentDescription("Falling notes aligned to the 88-key piano").assertIsDisplayed()
        compose.onNodeWithContentDescription("Played notes").assertIsDisplayed()
        compose.onNodeWithText("Listen").assertIsDisplayed()
    }
    @Test fun sheetScoreTapMovesBlueCursorToTouchedMeasure() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            vm.scale(0,ScaleKind.MAJOR,1,Hand.BOTH,true)
        }
        compose.waitUntil(15_000){vm.ui.value.score!=null}
        compose.runOnUiThread {vm.setView(ScoreView.SHEET)}
        fun find(view: android.view.View): WebView? {
            if(view is WebView)return view
            if(view is android.view.ViewGroup)for(i in 0 until view.childCount){
                val result=find(view.getChildAt(i));if(result!=null)return result
            }
            return null
        }
        val rendered=AtomicBoolean(false)
        compose.waitUntil(30_000) {
            compose.runOnUiThread {
                find(compose.activity.window.decorView)?.evaluateJavascript(
                    "typeof entries!=='undefined' && entries.length>5 && ready && canSeek && hitGroups.length>0"
                ){if(it=="true")rendered.set(true)}
            }
            rendered.get()
        }
        val tapped=AtomicBoolean(false)
        val diagnostic=java.util.concurrent.atomic.AtomicReference("No JS result")
        compose.runOnUiThread {
            find(compose.activity.window.decorView)?.evaluateJavascript("""
                (() => {
                  const e=entries.find(n=>n.beat>=2 && n.staff===1);
                  if(!e)return JSON.stringify({error:'No note at beat 2'});
                  const pt=e.svg.createSVGPoint();pt.x=e.x;pt.y=e.y;
                  const matrix=e.svg.getScreenCTM();
                  if(!matrix)return JSON.stringify({error:'SVG transform missing'});
                  const pos=pt.matrixTransform(matrix);
                  const mapped=beatAtClient(pos.x,pos.y);
                  document.getElementById('score').dispatchEvent(new MouseEvent('click',{
                    bubbles:true,clientX:pos.x,clientY:pos.y
                  }));
                  return JSON.stringify({beat:e.beat,mapped:mapped,ready:ready,seek:canSeek,groups:hitGroups.length});
                })()
            """.trimIndent()) { value ->
                diagnostic.set(value)
                tapped.set(value.contains("mapped"))
            }
        }
        compose.waitUntil(10_000){tapped.get()}
        compose.waitUntil(10_000) { vm.ui.value.transportBeat >= 1.5 || diagnostic.get().contains("error") }
        assertTrue("Tapping a score note should seek near its beat. JS=${diagnostic.get()} transport=${vm.ui.value.transportBeat}",
            vm.ui.value.transportBeat>=1.5)
    }
    @Test fun liveNoteHistoryKeepsSequenceAndClosesDurationOnRelease() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            vm.clearPlayedSequence()
            val now=System.nanoTime()
            vm.midi.simulate(MidiEvent.On(60,80,now))
            vm.midi.simulate(MidiEvent.Off(60,0,now+500_000_000))
            vm.midi.simulate(MidiEvent.On(64,80,now+2_700_000_000))
            vm.midi.simulate(MidiEvent.Off(64,0,now+3_100_000_000))
        }
        compose.waitUntil(5000){vm.playedSequence.value.size==2}
        val entries=vm.playedSequence.value
        assertEquals(listOf(60,64),entries.map{it.pitch})
        assertEquals(500L,entries[0].durationMs)
        assertEquals(400L,entries[1].durationMs)
    }
    @Test fun selectedLoopChangesTheRealPracticeBounds() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            vm.scale(0,ScaleKind.MAJOR,1,Hand.BOTH,true)
        }
        compose.waitUntil(15000){vm.ui.value.score!=null}
        compose.runOnUiThread {
            vm.edit {it.copy(loop=false)}
            vm.setPassage(.5,3.5)
        }
        compose.waitUntil(5000){vm.ui.value.startOffset>=.5}
        val p=vm.ui.value
        assertTrue(p.loop)
        assertEquals(.5,p.score!!.measures[p.startMeasure-1].start+p.startOffset,.01)
        assertEquals(3.5,p.score!!.measures[p.endMeasure-1].start+p.endOffset,.01)
    }
    @Test fun cursorSeekEndsScoredTakeWithoutChangingPassageBounds() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            vm.scale(0,ScaleKind.MAJOR,1,Hand.BOTH,true)
        }
        compose.waitUntil(15_000){vm.ui.value.score!=null}
        compose.runOnUiThread {
            vm.edit {it.copy(countIn=false,loop=false)}
            val before=vm.ui.value
            val start=before.score!!.measures[before.startMeasure-1].start+before.startOffset
            val end=before.score!!.measures[before.endMeasure-1].start+before.endOffset
            vm.start()
            assertTrue(vm.ui.value.running)
            vm.seek(2.0)
            assertFalse("Dragging a score cursor must end the graded take",vm.ui.value.running)
            assertEquals(2.0,vm.ui.value.transportBeat,0.01)
            val p=vm.ui.value
            assertEquals(start,p.score!!.measures[p.startMeasure-1].start+p.startOffset,0.01)
            assertEquals(end,p.score!!.measures[p.endMeasure-1].start+p.endOffset,0.01)
            vm.seek(1.0)
            val moved=vm.ui.value
            assertEquals(1.0,moved.transportBeat,0.01)
            assertEquals(start,moved.score!!.measures[moved.startMeasure-1].start+moved.startOffset,0.01)
            assertEquals(end,moved.score!!.measures[moved.endMeasure-1].start+moved.endOffset,0.01)
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
        compose.onNodeWithText("Studio").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Record new take").performScrollTo().assertIsDisplayed()
    }

    @Test fun voluntarySupportExplainsTheExternalProviderWithoutLaunchingPayment() {
        assertTrue(BuildConfig.SUPPORT_URL.startsWith("https://ko-fi.com/"))
        compose.onNodeWithContentDescription("Settings",useUnmergedTree=true).performClick()
        compose.onNodeWithText("About").performClick()
        compose.onNodeWithText("Support Piano Path").performScrollTo().performClick()
        compose.onNodeWithText("Open Ko-fi").assertIsDisplayed()
        compose.onNodeWithText("Maybe later").assertIsDisplayed().performClick()
    }

}
