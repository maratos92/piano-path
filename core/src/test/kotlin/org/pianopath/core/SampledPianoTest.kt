package org.pianopath.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs

class SampledPianoTest {
    private fun sample()=PianoSample(60,24000,ShortArray(24000){if(it%80<40)10000 else -10000})
    private fun audio(s: SampledPiano): FloatArray=FloatArray(4410).also{s.render(it)}
    @Test fun samplesRenderFinitePolyphonicSoundAndVelocityChangesLevel() {
        val s=SampledPiano(listOf(sample()));s.accept(MidiEvent.On(60,40,0));val soft=audio(s).sumOf{abs(it).toDouble()}
        s.silence();listOf(60,64,67).forEach{s.accept(MidiEvent.On(it,120,0))}
        val loud=audio(s);assertEquals(3,s.activeVoiceCount);assertTrue(loud.all{it.isFinite()&&abs(it)<=1});assertTrue(loud.sumOf{abs(it).toDouble()}>soft)
    }
    @Test fun sustainAndChannelReleaseDoNotAffectOtherChannels() {
        val s=SampledPiano(listOf(sample()));s.accept(MidiEvent.Control(64,127,0,1));s.accept(MidiEvent.On(60,80,0,1));s.accept(MidiEvent.Off(60,0,0,1))
        s.accept(MidiEvent.On(64,80,0,2));s.accept(MidiEvent.Control(120,0,0,2));assertEquals(1,s.activeVoiceCount)
        s.accept(MidiEvent.Control(64,0,0,1));repeat(20){audio(s)};assertEquals(0,s.activeVoiceCount)
    }
    @Test fun repeatedStrikesHaveSeparateVoicesAndZeroVelocityReleases() {
        val s=SampledPiano(listOf(sample()));repeat(2){s.accept(MidiEvent.On(60,80,0))};assertEquals(2,s.activeVoiceCount)
        s.accept(MidiEvent.On(60,0,0));s.accept(MidiEvent.Off(60,0,0));repeat(20){audio(s)};assertEquals(0,s.activeVoiceCount)
    }
    @Test fun voiceLimitBoundsSustainedInputAndPanicClearsIt() {
        val s=SampledPiano(listOf(sample()),maxVoices=128);repeat(300){s.accept(MidiEvent.On(it%88+21,80,0))};assertEquals(128,s.activeVoiceCount)
        s.silence();assertTrue(audio(s).all{it==0f})
    }
    @Test fun bundledWavsHaveCompleteAcousticKeyboardCoverageAndValidMetadata() {
        val directory=File("../app/src/main/assets/piano")
        assertEquals(30,directory.listFiles()!!.count{it.extension=="wav"})
        for(pitch in 21..108 step 3){val sample=PianoSample.read(pitch,File(directory,"$pitch.wav").inputStream());assertEquals(24000,sample.rate);assertTrue(sample.pcm.size>10000);assertTrue(sample.pcm.any{it.toInt()!=0})}
    }
    @Test(expected=IllegalArgumentException::class) fun malformedWavFailsClearly() {PianoSample.read(60,byteArrayOf(1,2,3).inputStream())}
}
