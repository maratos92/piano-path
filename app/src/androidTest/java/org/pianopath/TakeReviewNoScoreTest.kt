package org.pianopath

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pianopath.core.*
import org.pianopath.data.SessionEntity
import org.pianopath.data.TakeArchive

@RunWith(AndroidJUnit4::class)
class TakeReviewNoScoreTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun openingSavedTakeDoesNotCreateAnotherScoredSession() {
        val xml=Exercises.scale(octaves=1,hand=Hand.RIGHT).xml
        val score=MusicXml.parse(xml)
        val engine=PerformanceEngine(score,PracticeConfig(PracticeMode.RHYTHM,bpm=60.0,endBeat=4.0))
        val start=1_000_000_000L;engine.start(start)
        engine.accept(MidiEvent.On(score.notes.first{!it.rest}.midi,90,start))
        engine.accept(MidiEvent.Off(score.notes.first{!it.rest}.midi,0,start+250_000_000))
        val result=engine.finish(start+4_000_000_000)
        val name=TakeArchive.save(compose.activity,PracticeUi(score=score,xml=xml,bpm=60.0,endOffset=4.0),result)
        val category="Review test ${UUID.randomUUID()}"
        val session=SessionEntity(title=score.title,date=0,pitch=result.pitch,rhythm=result.rhythm,overall=result.overall,
            correct=result.correct,expected=result.expected,extra=result.extra,missing=result.missing,onsetMs=result.meanOnsetMs,durationMs=result.meanDurationMs,
            mode="RHYTHM",bpm=60.0,category=category,simulated=true,reviewFile=name)
        try {
            lateinit var vm: PianoViewModel
            compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java];vm.openReview(session)}
            compose.waitUntil(15_000){vm.ui.value.review && vm.ui.value.category==category}
            assertEquals(result.correct,vm.ui.value.result!!.correct)
            Thread.sleep(750)
            assertTrue("Viewing a saved take must not log a scored session",vm.progress.entries.value.none{it.category==category})
        } finally {File(compose.activity.filesDir,"takes/$name").delete()}
    }
}
