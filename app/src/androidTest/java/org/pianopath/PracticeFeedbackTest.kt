package org.pianopath

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pianopath.core.*

@RunWith(AndroidJUnit4::class)
class PracticeFeedbackTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun newlyLoadedPracticeDoesNotLoopAndSeekingShowsHeldNote() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            vm.scale(0,ScaleKind.MAJOR,1,Hand.RIGHT,true)
        }
        compose.waitUntil(15_000){vm.ui.value.score!=null}
        val initial=vm.ui.value
        assertFalse("A new practice session should play once by default",initial.loop)
        assertEquals("Newly loaded scores should open as sheet music",ScoreView.SHEET,initial.view)
        val first=initial.score!!.notes.first{!it.rest && it.hand==Hand.RIGHT}
        val middle=first.beat+first.duration/2
        compose.runOnUiThread {vm.seek(middle)}
        assertTrue("A note held across the cursor should appear on the keyboard",first.midi in vm.ui.value.expected)
        compose.runOnUiThread {vm.seek(initial.score!!.endBeat)}
        assertTrue("No keys remain expected at the end of the score",vm.ui.value.expected.isEmpty())
    }

    @Test fun loopRestWaitsAndPauseDoesNotConsumeItsRemainingTime() {
        lateinit var vm: PianoViewModel
        var previous=2
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            previous=vm.settings.value.loopBreakSeconds
            vm.setSettings{it.copy(loopBreakSeconds=2)}
            vm.scale(0,ScaleKind.MAJOR,1,Hand.RIGHT,true)
        }
        try {
            compose.waitUntil(15_000){vm.ui.value.score!=null}
            compose.runOnUiThread {
                vm.edit{it.copy(mode=PracticeMode.RHYTHM,countIn=false,metronome=false,loop=true,bpm=240.0,endMeasure=1,endOffset=1.0)}
                vm.start()
            }
            compose.waitUntil(10_000){vm.ui.value.feedback.startsWith("Rest ·")}
            compose.runOnUiThread {vm.togglePause()}
            Thread.sleep(2300)
            assertTrue(vm.ui.value.paused)
            assertTrue("The next repetition must not start during a paused rest",vm.ui.value.expected.isEmpty())
            assertEquals(1,vm.ui.value.loops.size)
            compose.runOnUiThread {vm.togglePause()}
            Thread.sleep(300)
            assertTrue("A two-second rest must not finish immediately on resume",vm.ui.value.expected.isEmpty())
            compose.waitUntil(5_000){vm.ui.value.loops.size>=2}
        } finally {
            compose.runOnUiThread {vm.stop();vm.setSettings{it.copy(loopBreakSeconds=previous)}}
        }
    }

    @Test fun enablingPassageNearCursorKeepsCursorAndDisablingRestoresWholeScore() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java];vm.scale(0,ScaleKind.MAJOR,1,Hand.RIGHT,true)}
        compose.waitUntil(15_000){vm.ui.value.score!=null}
        compose.runOnUiThread {
            vm.seek(4.0)
            vm.toggleLoopSelection()
            val p=vm.ui.value;val score=requireNotNull(p.score)
            val a=score.measures[p.startMeasure-1].start+p.startOffset
            val b=score.measures[p.endMeasure-1].start+p.endOffset
            assertTrue(p.loop);assertEquals(4.0,p.transportBeat,.01)
            assertTrue("The new passage should surround the cursor",a<=4.0 && b>=4.0)
            assertTrue("The suggested passage should be local",b-a<=4.01)
            vm.toggleLoopSelection()
            val off=vm.ui.value
            assertFalse(off.loop);assertEquals(4.0,off.transportBeat,.01)
            assertEquals(0.0,score.measures[off.startMeasure-1].start+off.startOffset,.01)
            assertEquals(score.endBeat,score.measures[off.endMeasure-1].start+off.endOffset,.01)
        }
    }

    @Test fun sightReadingStartsInSheetView() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java];vm.sight(1,0,4,Hand.RIGHT,0)}
        compose.waitUntil(15_000){vm.ui.value.category=="Sight reading" && vm.ui.value.score!=null}
        assertEquals(ScoreView.SHEET,vm.ui.value.view)
    }

    @Test fun countInLastsFourSecondsAtDifferentTempos() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java];vm.scale(0,ScaleKind.MAJOR,1,Hand.RIGHT,true)}
        compose.waitUntil(15_000){vm.ui.value.score!=null}
        try {
            for(bpm in listOf(60.0,240.0)) {
                compose.runOnUiThread {vm.edit{it.copy(bpm=bpm,countIn=true,loop=false,metronome=false)};vm.start()}
                val started=android.os.SystemClock.elapsedRealtime()
                compose.waitUntil(5_000){vm.ui.value.counting>0}
                Thread.sleep(2200)
                assertTrue("Count-in must still be active after 2.2 seconds at $bpm BPM",vm.ui.value.counting>0)
                compose.waitUntil(7_000){vm.ui.value.counting==0}
                val elapsed=android.os.SystemClock.elapsedRealtime()-started
                assertTrue("Count-in should finish near four seconds at $bpm BPM; was $elapsed ms",elapsed in 3300L..7000L)
                compose.runOnUiThread {vm.stop()}
            }
        } finally {compose.runOnUiThread {vm.stop()}}
    }

    @Test fun cancellationDoesNotSaveButExplicitFinishSavesPartialPractice() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java];vm.sight(1,0,4,Hand.RIGHT,0)}
        compose.waitUntil(15_000){vm.ui.value.category=="Sight reading" && vm.ui.value.score!=null}
        val scoreKey=vm.ui.value.key
        compose.runOnUiThread {
            vm.edit{it.copy(countIn=false,loop=false,metronome=false)}
            vm.start();assertTrue(vm.ui.value.running);vm.stop()
        }
        Thread.sleep(700)
        assertTrue("Cancelling must not add an accuracy record",vm.progress.entries.value.none{it.scoreKey==scoreKey})
        compose.runOnUiThread {vm.edit{it.copy(countIn=true)};vm.start();vm.stop(saveResult=true)}
        Thread.sleep(700)
        assertTrue("Finishing before the count-in ends must not save an empty result",vm.progress.entries.value.none{it.scoreKey==scoreKey})
        compose.runOnUiThread {
            vm.edit{it.copy(countIn=false)}
            vm.start();assertTrue(vm.ui.value.running)
            val pitch=vm.ui.value.score!!.notes.first{!it.rest && it.hand==Hand.RIGHT}.midi
            vm.midi.simulate(MidiEvent.On(pitch,80,System.nanoTime()))
        }
        Thread.sleep(100)
        compose.runOnUiThread {vm.stop(saveResult=true)}
        compose.waitUntil(10_000){vm.progress.entries.value.any{it.scoreKey==scoreKey}}
        assertEquals(1,vm.progress.entries.value.count{it.scoreKey==scoreKey})
    }
}
