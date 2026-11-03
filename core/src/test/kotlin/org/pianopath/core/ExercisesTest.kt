package org.pianopath.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExercisesTest {
    @Test fun cMajorTwoOctavesAndFingering() {
        val e=Exercises.scale(0,ScaleKind.MAJOR,2,Hand.RIGHT,true);val n=e.score.notes
        assertEquals(29,n.size);assertEquals(60,n.first().midi);assertEquals(84,n[14].midi);assertEquals(60,n.last().midi)
        assertEquals(listOf("1","2","3","1","2","3","4","1"),n.take(8).map{it.finger});assertEquals("5",n[14].finger)
    }
    @Test fun leftHandOctaveCrossingUsesThumb() {
        val n=Exercises.scale(0,ScaleKind.MAJOR,2,Hand.LEFT,false).score.notes
        assertEquals("5",n[0].finger);assertEquals("1",n[7].finger);assertEquals("1",n[14].finger)
    }
    @Test fun bMajorLeftStartsOnFour() {assertEquals("4",Exercises.scale(11,hand=Hand.LEFT).score.notes.first().finger)}
    @Test fun melodicMinorDescendsNaturally() {
        val n=Exercises.scale(9,ScaleKind.MELODIC_MINOR,1,Hand.RIGHT,true).score.notes.map{it.midi}
        assertTrue(78 in n.take(8));assertTrue(80 in n.take(8));assertEquals(listOf(79,77),n.drop(8).take(2))
    }
    @Test fun allScalesStayInMidiRangeAndRoundTrip() {
        for(root in 0..11)for(kind in ScaleKind.entries)for(octaves in 1..2){val ex=Exercises.scale(root,kind,octaves);val parsed=MusicXml.parse(ex.xml);assertEquals(ex.score.notes.size,parsed.notes.count{!it.rest});assertTrue(parsed.notes.filter{!it.rest}.all{it.midi in 21..108});assertTrue(parsed.notes.all{it.finger.isBlank()||it.finger.toInt() in 1..5})}
    }
    @Test fun uncuratedFingeringIsNotFabricated() {val e=Exercises.scale(1);assertTrue(e.score.notes.all{it.finger.isBlank()});assertTrue(e.guidance.contains("not been curated"))}
    @Test fun arpeggioIntervals() {assertEquals(listOf(60,64,67,72),Exercises.scale(0,ScaleKind.ARPEGGIO,hand=Hand.RIGHT,descending=false).score.notes.map{it.midi})}
    @Test fun sightGenerationIsDeterministicAndVaried() {
        val a=Exercises.sight(2,seed=42);val b=Exercises.sight(2,seed=42);val c=Exercises.sight(2,seed=43)
        assertEquals(a.xml,b.xml);assertNotEquals(a.xml,c.xml);assertTrue(a.score.notes.map{it.midi}.distinct().size>5)
    }
    @Test fun everyLevelAndTimeSignatureHasValidBoundedMeasures() {
        for(level in 0..3)for(beats in listOf(3,4,6))for(hand in Hand.entries){val e=Exercises.sight(level,beats=beats,hand=hand,seed=123);val parsed=MusicXml.parse(e.xml);assertEquals(e.score.endBeat,parsed.endBeat,.001);assertTrue(e.score.notes.all{n->n.duration>0 && n.beat+n.duration<=e.score.measures[n.measure-1].let{it.start+it.length}+.001})}
    }
    @Test fun singleHandExercisesHaveOnlyOnePitchedStaff() {for(h in listOf(Hand.LEFT,Hand.RIGHT)){assertTrue(Exercises.sight(hand=h,seed=1).score.notes.all{it.hand==h})}}
    @Test fun packagedLibraryContainsFiftyLegalParsableStudies() {
        val dir=File("../app/src/main/assets/scores");val files=dir.listFiles{f->f.extension=="musicxml"} ?: emptyArray()
        assertEquals(50,files.size)
        for(f in files){val xml=f.readText();assertTrue(xml.contains("CC0-1.0"));val score=MusicXml.parse(xml);assertTrue(score.notes.count{!it.rest}>20);assertTrue(score.measures.size>=8)}
    }
    @Test fun fMajorRightHandEndsWithFour() { assertEquals("4",Exercises.scale(5,hand=Hand.RIGHT,descending=false).score.notes.last().finger) }
}
