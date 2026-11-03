package org.pianopath.core

import org.junit.Assert.*
import org.junit.Test

class MidiTest {
    private fun bytes(vararg values: Int)=values.map{it.toByte()}.toByteArray()
    @Test fun runningStatusAndZeroVelocity() {
        val out=mutableListOf<MidiEvent>();val p=MidiParser(out::add)
        p.feed(bytes(0x90,60,80,64,90,60,0),timestamp=100)
        assertEquals(listOf(MidiEvent.On(60,80,100),MidiEvent.On(64,90,100),MidiEvent.Off(60,0,100)),out)
    }
    @Test fun fragmentedMessagesAndRealtime() {
        val out=mutableListOf<MidiEvent>();val p=MidiParser(out::add)
        p.feed(bytes(0x92,60),timestamp=10);p.feed(bytes(0xf8,99,64,0xfa,75),timestamp=20)
        assertEquals(listOf(MidiEvent.On(60,99,20,2),MidiEvent.On(64,75,20,2)),out)
    }
    @Test fun sysexAndSystemCommonCancelRunningStatus() {
        val out=mutableListOf<MidiEvent>();val p=MidiParser(out::add)
        p.feed(bytes(0x90,60,80,0xf0,1,0xf8,2,0xf7,64,90,0xf1,22,65,80,0x80,60,12),timestamp=1)
        assertEquals(2,out.size);assertTrue(out.last() is MidiEvent.Off)
    }
    @Test fun sustainSeparatesHeldAndSoundingByChannel() {
        val k=KeyboardState();k.accept(MidiEvent.On(60,88,0));k.accept(MidiEvent.Control(64,127,1));k.accept(MidiEvent.Off(60,0,2))
        assertTrue(k.held.isEmpty());assertTrue(0 to 60 in k.sounding)
        k.accept(MidiEvent.On(64,90,3,1));k.accept(MidiEvent.Control(64,0,4))
        assertEquals(setOf(1 to 64),k.sounding)
    }
    @Test fun repeatedNoteOffDoesNotStick() {
        val k=KeyboardState();repeat(3){k.accept(MidiEvent.On(60,100,it.toLong()));k.accept(MidiEvent.Off(60,0,it.toLong()+1))}
        assertTrue(k.held.isEmpty());assertTrue(k.sounding.isEmpty())
    }
    @Test fun panicAndResetClearSustain() {
        val k=KeyboardState();k.accept(MidiEvent.On(60,100,0));k.accept(MidiEvent.Control(64,127,0));k.accept(MidiEvent.Control(123,0,1))
        assertTrue(k.sounding.isEmpty());assertTrue(k.sustain.isEmpty());k.reset();assertTrue(k.held.isEmpty())
    }
    @Test fun programChangeDoesNotCorruptNextNote() {
        val out=mutableListOf<MidiEvent>();MidiParser(out::add).feed(bytes(0xc0,4,5,0x90,72,80),timestamp=0)
        assertEquals(listOf(MidiEvent.On(72,80,0)),out)
    }
    @Test fun decoderResetDropsPartialMessage() {
        val out=mutableListOf<MidiEvent>();val p=MidiParser(out::add);p.feed(bytes(0x90,60),timestamp=0);p.reset();p.feed(bytes(80),timestamp=1);assertTrue(out.isEmpty())
    }
}
