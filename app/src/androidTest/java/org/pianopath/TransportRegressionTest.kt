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
class TransportRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun scale(): PianoViewModel {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {
            vm = ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            vm.scale(0, ScaleKind.MAJOR, 1, Hand.RIGHT, true)
        }
        compose.waitUntil(15_000) { vm.ui.value.category == "Scales" && vm.ui.value.score != null }
        compose.runOnUiThread { vm.edit { it.copy(hand=Hand.RIGHT, mode=PracticeMode.RHYTHM, countIn=false, metronome=false, loop=false, bpm=120.0) } }
        return vm
    }

    @Test fun stopFreezesPracticeAndListeningAtTheirCurrentCursor() {
        val vm = scale()
        try {
            compose.runOnUiThread { vm.start() }
            compose.waitUntil(5_000) { vm.ui.value.running && vm.ui.value.transportBeat > 0.1 }
            compose.runOnUiThread { vm.stop() }
            val stopped = vm.ui.value
            assertFalse(stopped.running)
            assertFalse(stopped.listening)
            Thread.sleep(400)
            assertEquals(stopped.transportBeat, vm.ui.value.transportBeat, 0.001)
            assertEquals(stopped.beat, vm.ui.value.beat, 0.001)
            assertFalse(vm.ui.value.running)

            compose.runOnUiThread { vm.listen() }
            compose.waitUntil(5_000) { vm.ui.value.listening && vm.ui.value.transportBeat > stopped.transportBeat + 0.1 }
            compose.runOnUiThread { vm.stop() }
            val playbackStopped = vm.ui.value
            assertFalse(playbackStopped.listening)
            assertTrue(playbackStopped.playbackHeld.isEmpty())
            Thread.sleep(400)
            assertEquals(playbackStopped.transportBeat, vm.ui.value.transportBeat, 0.001)
            assertEquals(playbackStopped.beat, vm.ui.value.beat, 0.001)
            assertFalse(vm.ui.value.listening)

            compose.runOnUiThread { vm.listen() }
            compose.waitUntil(5_000) { vm.ui.value.listening && vm.ui.value.transportBeat > playbackStopped.transportBeat + 0.1 }
            compose.runOnUiThread { vm.stopListening() }
            val directStopBeat = vm.ui.value.transportBeat
            Thread.sleep(400)
            assertFalse(vm.ui.value.listening)
            assertEquals(directStopBeat, vm.ui.value.transportBeat, 0.001)
        } finally { compose.runOnUiThread { vm.stop() } }
    }

    @Test fun startingEitherTransportReplacesTheOtherEvenWhenPaused() {
        val vm = scale()
        try {
            compose.runOnUiThread {
                vm.start()
                assertTrue(vm.ui.value.running)
                vm.togglePause()
                assertTrue(vm.ui.value.paused)
                vm.listen()
                assertTrue(vm.ui.value.listening)
                assertFalse(vm.ui.value.running)
                assertFalse(vm.ui.value.paused)
                vm.togglePause()
                assertTrue(vm.ui.value.paused)
                vm.start()
                assertTrue(vm.ui.value.running)
                assertFalse(vm.ui.value.listening)
                assertFalse(vm.ui.value.paused)
            }
            Thread.sleep(300)
            assertTrue(vm.ui.value.running)
            assertFalse(vm.ui.value.listening)
        } finally { compose.runOnUiThread { vm.stop() } }
    }

    @Test fun waitModeAcceptsCorrectNoteAfterManyMistakes() {
        val vm = scale()
        try {
            compose.runOnUiThread { vm.edit { it.copy(mode=PracticeMode.WAIT) }; vm.start() }
            compose.waitUntil(5_000) { vm.ui.value.running && vm.ui.value.expected.isNotEmpty() }
            // Let the practice coroutine initialize its engine before injecting MIDI.
            Thread.sleep(100)
            val first = vm.ui.value.expected.single()
            val wrong = if (first == 60) 61 else 60
            compose.runOnUiThread {
                repeat(400) {
                    vm.midi.simulate(MidiEvent.On(wrong, 80, System.nanoTime()))
                    vm.midi.simulate(MidiEvent.Off(wrong, 0, System.nanoTime()))
                }
                vm.midi.simulate(MidiEvent.On(first, 80, System.nanoTime()))
                vm.midi.simulate(MidiEvent.Off(first, 0, System.nanoTime()))
            }
            compose.waitUntil(3_000) { vm.ui.value.beat > 0.0 }
            assertTrue("The first correct note should be matched after the errors", vm.ui.value.matched.isNotEmpty())
        } finally { compose.runOnUiThread { vm.stop() } }
    }

    @Test fun followFinishesWhenLastNotesAreMissed() {
        val vm = scale()
        try {
            compose.runOnUiThread {
                vm.edit { it.copy(mode=PracticeMode.FOLLOW, countIn=false, metronome=true,
                    loop=false, bpm=240.0, endMeasure=1, endOffset=2.0) }
                vm.start()
            }
            compose.waitUntil(5_000) { vm.ui.value.running && vm.ui.value.expected.isNotEmpty() }
            val first = vm.ui.value.expected.first()
            compose.runOnUiThread {
                val now=System.nanoTime()
                vm.midi.simulate(MidiEvent.On(first,80,now))
                vm.midi.simulate(MidiEvent.Off(first,0,now+10_000_000))
            }
            compose.waitUntil(5_000) { vm.ui.value.matched.isNotEmpty() }
            compose.waitUntil(8_000) { !vm.ui.value.running && vm.ui.value.result != null }
            val finished=vm.ui.value
            assertTrue("Unplayed final notes must be counted as missing", finished.result!!.missing > 0)
            assertFalse(finished.listening)
            assertFalse(finished.paused)
            Thread.sleep(400)
            assertFalse("Practice and its metronome must stay stopped", vm.ui.value.running)
            assertEquals(finished.transportBeat,vm.ui.value.transportBeat,0.001)
        } finally { compose.runOnUiThread { vm.stop() } }
    }

    @Test fun sightPreviewReplacesCountInAndNextExerciseKeepsSettings() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {
            vm = ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            vm.sight(1, 2, 4, Hand.RIGHT, 2, 137.0)
        }
        compose.waitUntil(15_000) { vm.ui.value.category == "Sight reading" && vm.ui.value.score != null }
        val firstKey = vm.ui.value.key
        try {
            compose.runOnUiThread { vm.edit { it.copy(countIn=true, metronome=false) }; vm.start() }
            compose.waitUntil(3_000) { vm.ui.value.feedback.startsWith("Preview ·") }
            assertTrue("Preview must display a countdown", vm.ui.value.counting > 0)
            compose.waitUntil(5_000) { vm.ui.value.running && !vm.ui.value.feedback.startsWith("Preview ·") }
            assertEquals("Preview replaces the separate count-in", 0, vm.ui.value.counting)
            assertFalse(vm.ui.value.feedback.startsWith("Count in ·"))
            compose.runOnUiThread { vm.stop(); vm.nextSight() }
            compose.waitUntil(15_000) { vm.ui.value.key != firstKey && vm.ui.value.category == "Sight reading" }
            val next = vm.ui.value
            assertEquals(Hand.RIGHT, next.hand)
            assertEquals(2, next.previewSeconds)
            assertEquals(137.0, next.bpm, 0.01)
            assertEquals(2, requireNotNull(next.score).fifths)
            assertEquals(4, next.score!!.beats)
            assertEquals("The selected difficulty determines the number of measures", 6, next.score!!.measures.size)
            assertTrue(next.score!!.notes.filterNot { it.rest }.all { it.hand == Hand.RIGHT })
        } finally { compose.runOnUiThread { vm.stop() } }
    }
}
