package org.pianopath

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.pianopath.core.*
import org.pianopath.data.SessionEntity
import org.pianopath.data.TakeArchive

@RunWith(AndroidJUnit4::class)
class ArchiveSafetyTest {
    @Test fun reviewRetainsMissingNotesAndRejectsCorruptBoundsAndDurations() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(base.cacheDir,"archive-test-${UUID.randomUUID()}").apply{mkdirs()}
        val context=object: ContextWrapper(base) {override fun getFilesDir(): File = directory}
        try {
            val xml=Exercises.scale(octaves=1,hand=Hand.RIGHT).xml
            val score=MusicXml.parse(xml)
            val engine=PerformanceEngine(score,PracticeConfig(PracticeMode.RHYTHM,bpm=60.0,endBeat=4.0))
            val start=1_000_000_000L;engine.start(start)
            engine.accept(MidiEvent.On(score.notes.first{!it.rest}.midi,90,start))
            engine.accept(MidiEvent.Off(score.notes.first{!it.rest}.midi,0,start+250_000_000))
            val result=engine.finish(start+4_000_000_000)
            assertTrue(result.details.any{it.missing && it.channel == -1})
            val practice=PracticeUi(score=score,xml=xml,bpm=60.0,endOffset=4.0)
            val name=TakeArchive.save(context,practice,result)
            val session=SessionEntity(title=score.title,date=0,pitch=result.pitch,rhythm=result.rhythm,overall=result.overall,
                correct=result.correct,expected=result.expected,extra=result.extra,missing=result.missing,onsetMs=result.meanOnsetMs,durationMs=result.meanDurationMs,
                mode="RHYTHM",bpm=60.0,category="Scales",simulated=true,reviewFile=name)
            assertEquals(result.missing,TakeArchive.load(context,session).result!!.missing)
            val file=File(directory,"takes/$name");val original=file.readText()
            file.writeText(JSONObject(original).put("endMeasure",9999).toString())
            assertThrows(IllegalArgumentException::class.java){TakeArchive.load(context,session)}
            val invalidDuration=JSONObject(original)
            invalidDuration.getJSONArray("take").getJSONObject(0).put("duration",-1)
            file.writeText(invalidDuration.toString())
            assertThrows(IllegalArgumentException::class.java){TakeArchive.load(context,session)}
        } finally {directory.deleteRecursively()}
    }
}
