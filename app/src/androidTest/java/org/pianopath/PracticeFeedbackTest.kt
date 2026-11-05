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
}
