package org.pianopath

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.pianopath.core.*
import org.pianopath.data.RecordingRepository
import org.pianopath.data.LocalProgressStore
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalPracticeTest {
    private fun withRepository(test: (RecordingRepository, File) -> Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(context.cacheDir,"recording-test-${UUID.randomUUID()}")
        try { test(RecordingRepository(dir),dir) } finally { dir.deleteRecursively() }
    }

    @Test fun savedRecordingSurvivesReopeningRenameAndDelete() = withRepository { repo,dir ->
        val id=UUID.randomUUID().toString()
        val take=MidiRecording(id,"First",100,500,listOf(RecordedMidi(0,0,2,60,82),RecordedMidi(500,1,2,60,17)))
        repo.save(take)
        val reopened=RecordingRepository(dir)
        assertEquals(id,reopened.load().recordings.single().id)
        assertEquals(take,reopened.read(id))
        reopened.save(take.copy(title="Renamed"))
        assertEquals("Renamed",RecordingRepository(dir).load().recordings.single().title)
        // A delayed checkpoint must not resurrect an older draft after save.
        reopened.checkpoint(take.copy(title="Old draft",finished=false))
        assertEquals("Renamed",RecordingRepository(dir).read(id).title)
        assertEquals(1,RecordingRepository(dir).load().recordings.size)
        reopened.delete(id)
        assertTrue(RecordingRepository(dir).load().recordings.isEmpty())
    }

    @Test fun interruptedDraftIsRecoveredAndClosedOnReopen() = withRepository { repo,dir ->
        val id=UUID.randomUUID().toString()
        repo.checkpoint(MidiRecording(id,"Interrupted",100,1000,listOf(
            RecordedMidi(0,2,1,64,127),RecordedMidi(10,0,1,60,90)),finished=false))
        val loaded=RecordingRepository(dir).load()
        assertEquals(id,loaded.recordings.single().id)
        val recovered=RecordingRepository(dir).read(id)
        assertTrue(recovered.finished);assertTrue(recovered.recovered);assertTrue(loaded.messages.isNotEmpty())
        val state=RecordedMidiState();recovered.events.forEach(state::accept)
        assertTrue(state.soundingPitches.isEmpty())
        assertEquals(recovered,RecordingRepository(dir).read(id))
    }

    @Test fun corruptFileIsPreservedAndDoesNotHideValidRecordings() = withRepository { repo,dir ->
        val id=UUID.randomUUID().toString()
        val take=MidiRecording(id,"Valid",100,0,emptyList())
        repo.save(take)
        val wrongId=UUID.randomUUID().toString()
        File(dir,"$wrongId.json").writeText(File(dir,"$id.json").readText())
        assertThrows(IllegalArgumentException::class.java){repo.read(wrongId)}
        val bad=File(dir,"${UUID.randomUUID()}.json");bad.writeText("{broken")
        val loaded=RecordingRepository(dir).load()
        assertEquals(listOf(take),loaded.recordings);assertTrue(loaded.messages.isNotEmpty())
        assertTrue(dir.listFiles()!!.any{it.name.contains(".corrupt-") && it.readText()=="{broken"})
    }

    @Test fun progressAndGoalSurviveReopeningAndCorruptEntriesAreKept() = runBlocking {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val suffix=UUID.randomUUID().toString()
        val dir=File(base.cacheDir,"progress-test-$suffix").apply{mkdirs()}
        val prefsName="test-goals-$suffix"
        val context=object: ContextWrapper(base) {
            override fun getFilesDir(): File = dir
            override fun getSharedPreferences(name: String?, mode: Int) = base.getSharedPreferences(prefsName,mode)
        }
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val messages=java.util.Collections.synchronizedList(mutableListOf<String>())
            val first=LocalProgressStore(context,scope){messages.add(it)}
            val entry=PracticeEntry(UUID.randomUUID().toString(),1234,"Intervals","Ear training",activeMs=12_000,correct=4,total=5,roundId="round",exercise="interval")
            first.add(entry);first.setGoal(25)
            val broken=File(dir,"progress/${UUID.randomUUID()}.json");broken.writeText("{broken")
            // A valid payload under a different filename must not impersonate a saved entry.
            val mismatched=File(dir,"progress/${UUID.randomUUID()}.json")
            mismatched.writeText(File(dir,"progress/${entry.id}.json").readText())
            val reopened=LocalProgressStore(context,scope){messages.add(it)}
            reopened.reload()
            assertEquals(listOf(entry),reopened.entries.value)
            assertEquals(25,reopened.goalMinutes.value)
            assertTrue(messages.isNotEmpty());assertEquals("{broken",broken.readText());assertTrue(mismatched.exists())
        } finally {scope.cancel();dir.deleteRecursively();base.deleteSharedPreferences(prefsName)}
    }
}

@RunWith(AndroidJUnit4::class)
class FreePlayIntegrationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun simulatedInputCanBeSavedReopenedAndSoughtWithoutAnAccuracyResult() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]}
        compose.waitUntil(15_000){vm.recorder.state.value.ready}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Free play recordings"))
        compose.onNodeWithText("Free play recordings").performClick()
        compose.onNodeWithText("Free play recordings").assertIsDisplayed()
        compose.onNodeWithText("Record new take").performClick()
        compose.waitUntil(5_000){vm.recorder.state.value.recording}
        compose.runOnUiThread {vm.midi.simulate(MidiEvent.On(60,88,System.nanoTime(),1))}
        compose.waitUntil(5_000){vm.recorder.state.value.eventCount>=1}
        compose.runOnUiThread {vm.midi.simulate(MidiEvent.Off(60,12,System.nanoTime(),1))}
        compose.waitUntil(5_000){vm.recorder.state.value.eventCount>=2}
        compose.runOnUiThread {vm.recorder.stop()}
        val id=requireNotNull(vm.recorder.state.value.current).id
        try {
            compose.waitUntil(10_000){!vm.recorder.state.value.saving && vm.recorder.recordings.value.any{it.id==id}}
            compose.runOnUiThread {vm.recorder.open(id)}
            compose.waitUntil(10_000){vm.recorder.state.value.status=="Ready" && vm.recorder.state.value.current?.id==id}
            val take=requireNotNull(vm.recorder.state.value.current)
            assertTrue(take.events.any{it.kind==0 && it.channel==1 && it.a==60 && it.b==88})
            assertTrue(take.events.any{it.kind==1 && it.channel==1 && it.a==60 && it.b==12})
            compose.runOnUiThread {vm.recorder.seek(take.durationUs/2_000_000.0)}
            assertNull(vm.ui.value.result)
        } finally {
            compose.runOnUiThread {vm.recorder.delete(id)}
            compose.waitUntil(10_000){!vm.recorder.state.value.saving}
        }
    }

    @Test fun progressIsReachableFromHomeAndShowsGoal() {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Your practice · goals & progress"))
        compose.onNodeWithText("Your practice · goals & progress").performClick()
        compose.onNodeWithText("Your practice").assertIsDisplayed()
        compose.onNodeWithText("Daily goal",substring=true).assertIsDisplayed()
    }

    @Test fun repeatedSimultaneousNotesKeepTheirFifoReleaseDurations() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java]
            vm.clearPlayedSequence()
            val t=System.nanoTime()
            vm.midi.simulate(MidiEvent.On(60,50,t))
            vm.midi.simulate(MidiEvent.On(60,90,t))
            vm.midi.simulate(MidiEvent.Off(60,0,t+100_000_000))
            vm.midi.simulate(MidiEvent.Off(60,0,t+200_000_000))
        }
        compose.waitUntil(5_000){vm.playedSequence.value.size==2 && vm.playedSequence.value.all{it.durationMs!=null}}
        assertEquals(listOf(100L,200L),vm.playedSequence.value.map{it.durationMs})
        compose.runOnUiThread {
            val t=System.nanoTime()
            vm.midi.simulate(MidiEvent.On(64,80,t,3))
            vm.midi.simulate(MidiEvent.Control(123,0,t+300_000_000,3))
        }
        compose.waitUntil(5_000){vm.playedSequence.value.size==3 && vm.playedSequence.value.last().durationMs!=null}
        assertEquals(300L,vm.playedSequence.value.last().durationMs)
    }
}
