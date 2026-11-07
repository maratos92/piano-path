package org.pianopath.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.*

class MusicXmlTest {
    private fun wrap(notes: String,attributes: String="")="""<score-partwise><work><work-title>Fixture</work-title></work><identification><creator type="composer">Tester</creator></identification><part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list><part id="P1"><measure number="1"><attributes><divisions>4</divisions><key><fifths>-2</fifths></key><time><beats>3</beats><beat-type>4</beat-type></time>$attributes</attributes>$notes</measure></part></score-partwise>"""
    private val c="<pitch><step>C</step><octave>4</octave></pitch>"
    @Test fun parsesWrittenGraceAndTrillWithoutChangingAssessmentNotes() {
        val xml=wrap("""<note><grace slash="yes"/><pitch><step>D</step><octave>4</octave></pitch><voice>1</voice><staff>1</staff></note><note>$c<duration>4</duration><voice>1</voice><staff>1</staff><notations><ornaments><trill-mark/></ornaments></notations></note>""")
        val score=MusicXml.parse(xml)
        assertEquals(1,score.graceNotes.size)
        assertEquals(62,score.graceNotes.single().midi)
        assertEquals(1,score.notes.size)
        assertEquals(OrnamentKind.TRILL,score.ornaments.single().kind)
        assertEquals(1,score.selected(0.0,score.endBeat,Hand.BOTH).size)
        assertTrue(PlaybackPlan.create(score,0.0,score.endBeat).size>2)
    }
    @Test fun chordBackupMultipleVoicesAndFingering() {
        val xml=wrap("""<direction><sound tempo="96"/></direction><note>$c<duration>6</duration><voice>1</voice><type>quarter</type><dot/><staff>1</staff><notations><technical><fingering>1</fingering></technical></notations></note><note><chord/><pitch><step>E</step><alter>-1</alter><octave>4</octave></pitch><duration>6</duration><voice>1</voice><staff>1</staff></note><note><rest/><duration>6</duration><voice>1</voice></note><backup><duration>12</duration></backup><note><pitch><step>C</step><octave>3</octave></pitch><duration>12</duration><voice>2</voice><staff>2</staff></note>""")
        val score=MusicXml.parse(xml);assertEquals(4,score.notes.size);assertEquals(0.0,score.notes[1].beat,.01);assertEquals(63,score.notes[1].midi);assertEquals(1.5,score.notes[0].duration,.01);assertEquals("1",score.notes[0].finger);assertEquals(Hand.LEFT,score.notes.last().hand);assertEquals(96.0,score.tempo,.01);assertEquals(-2,score.fifths)
    }
    @Test fun tempoMapIncludesLaterMeasuresAndSupportsSeeking() {
        val initial=wrap("<direction><sound tempo=\"100\"/></direction><note>$c<duration>12</duration></note>")
        val second="<measure number=\"2\"><direction><direction-type><words>accelerando</words></direction-type><sound tempo=\"140\"/></direction><note>$c<duration>12</duration></note></measure>"
        val score=MusicXml.parse(initial.replace("</part>",second+"</part>"))
        assertEquals(100.0,score.tempo,.001)
        assertEquals(2,score.tempoMarks.size)
        assertEquals(140.0,score.tempoAt(3.0),.001)
        assertTrue(score.expressiveTempo)
        assertEquals(1.8,score.secondsBetween(0.0,3.0),.001)
        assertEquals(3.5,score.beatAtSeconds(0.0,1.8+0.5*60/140,6.0),.001)
    }
    @Test fun tiesMergeWithoutRetriggerAndPreserveDuration() {
        val xml=wrap("<note>$c<duration>4</duration><tie type=\"start\"/></note><note>$c<duration>4</duration><tie type=\"stop\"/></note><note><rest/><duration>4</duration></note>")
        val s=MusicXml.parse(xml);assertEquals(2,s.notes.size);assertEquals(2.0,s.notes[0].duration,.01)
    }
    @Test fun transposeAndForwardAreApplied() {
        val s=MusicXml.parse(wrap("<forward><duration>4</duration></forward><note>$c<duration>8</duration></note>","<transpose><chromatic>2</chromatic></transpose>"));assertEquals(62,s.notes[0].midi);assertEquals(1.0,s.notes[0].beat,.01)
    }
    private fun zip(entries: Map<String,String>): ByteArray {val out=ByteArrayOutputStream();ZipOutputStream(out).use{z->entries.forEach{(k,v)->z.putNextEntry(ZipEntry(k));z.write(v.toByteArray());z.closeEntry()}};return out.toByteArray()}
    @Test fun mxlUsesContainerRootfile() {
        val xml=wrap("<note>$c<duration>12</duration></note>")
        val archive=zip(mapOf("META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"score/piano.musicxml\"/></rootfiles></container>","score/piano.musicxml" to xml))
        assertEquals("Fixture",MusicXml.parse(MusicXml.unpack(archive)).title)
    }
    @Test(expected=IllegalArgumentException::class) fun unsafeArchivePathsAreRejected() { MusicXml.unpack(zip(mapOf("../score.xml" to "no"))) }
    @Test(expected=IllegalArgumentException::class) fun externalEntityDeclarationsAreRejected() {
        val xml=wrap("<note>$c<duration>12</duration></note>").replace("<score-partwise>","<!DOCTYPE score-partwise [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><score-partwise>").replace("Fixture","&xxe;")
        val s=MusicXml.parse(xml);assertFalse(s.title.contains("root:"))
    }
    @Test(expected=IllegalArgumentException::class) fun timewiseFailsWithUsefulError() {MusicXml.parse("<score-timewise/>")}
    @Test(expected=IllegalArgumentException::class) fun emptyScoreFails() {MusicXml.parse(wrap("<note><rest/><duration>12</duration></note>"))}
    @Test(expected=IllegalArgumentException::class) fun mxlContainerEntityDeclarationsAreRejected() {
        MusicXml.unpack(zip(mapOf("META-INF/container.xml" to "<!DOCTYPE container [<!ENTITY x 'score.xml'>]><container><rootfiles><rootfile full-path='&x;'/></rootfiles></container>","score.xml" to wrap("<note>$c<duration>12</duration></note>"))))
    }
}
