package org.pianopath.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PianoSynthTest {
    private fun rms(a: FloatArray)=sqrt(a.sumOf{it.toDouble()*it}/a.size)
    private fun render(s: PianoSynth,seconds: Double): FloatArray=FloatArray((s.sampleRate*seconds).toInt()).also{s.render(it)}
    @Test fun silentUntilAKeyIsPressed() {val synth=PianoSynth();assertEquals(0.0,rms(render(synth,.1)),0.0)}
    @Test fun rendersAudibleBoundedFinitePolyphonicPcm() {
        val s=PianoSynth();listOf(48,60,64,67,72).forEach{s.accept(MidiEvent.On(it,100,0))}
        val samples=render(s,.3);assertTrue(rms(samples)>.02);assertTrue(samples.all{it.isFinite()&&abs(it)<1});assertEquals(5,s.activeVoiceCount)
    }
    @Test fun velocityChangesActualAudioAmplitude() {
        fun level(v: Int): Double {val s=PianoSynth();s.accept(MidiEvent.On(60,v,0));return rms(render(s,.1))}
        assertTrue(level(110)>level(30)*2)
    }
    @Test fun concertACarriesItsExpectedFundamental() {
        val s=PianoSynth();s.accept(MidiEvent.On(69,100,0));val samples=render(s,.2)
        fun power(hz: Double): Double {var re=0.0;var im=0.0;for(i in samples.indices){re+=samples[i]*cos(i*2*PI*hz/s.sampleRate);im+=samples[i]*sin(i*2*PI*hz/s.sampleRate)};return re*re+im*im}
        assertTrue(power(440.0)>power(430.0)*10);assertTrue(power(440.0)>power(450.0)*10)
    }
    @Test fun noteOffDecaysAndEventuallyRemovesVoice() {
        val s=PianoSynth();s.accept(MidiEvent.On(60,100,0));render(s,.1);s.accept(MidiEvent.Off(60,0,0));render(s,.6)
        assertEquals(0,s.activeVoiceCount);assertEquals(0.0,rms(render(s,.1)),0.0)
    }
    @Test fun sustainHoldsReleasedNotesAndPedalUpReleasesThem() {
        val s=PianoSynth();s.accept(MidiEvent.Control(64,127,0));s.accept(MidiEvent.On(60,100,0));s.accept(MidiEvent.Off(60,0,0))
        assertTrue(rms(render(s,.3))>.005);s.accept(MidiEvent.Control(64,0,0));render(s,.6);assertEquals(0,s.activeVoiceCount)
    }
    @Test fun repeatedNotesAndIndependentChannelsReleaseCorrectly() {
        val s=PianoSynth();repeat(2){s.accept(MidiEvent.On(60,80,0))};s.accept(MidiEvent.On(60,80,0,2))
        s.accept(MidiEvent.Off(60,0,0));render(s,.6);assertEquals(2,s.activeVoiceCount)
        s.releaseChannel(2);render(s,.6);assertEquals(1,s.activeVoiceCount)
        s.accept(MidiEvent.On(60,0,0));render(s,.6);assertEquals(0,s.activeVoiceCount)
    }
    @Test fun voiceLimitAndPanicPreventUnboundedOrStuckAudio() {
        val s=PianoSynth(maxVoices=16);for(p in 21..108)s.accept(MidiEvent.On(p,127,0))
        assertEquals(16,s.activeVoiceCount);s.accept(MidiEvent.Control(123,0,0));render(s,.6);assertEquals(0,s.activeVoiceCount)
        s.accept(MidiEvent.On(60,100,0));s.silence();assertEquals(0.0,rms(render(s,.1)),0.0)
    }
}
