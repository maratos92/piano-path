package org.pianopath.midi

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.pianopath.core.MidiEvent

@RunWith(AndroidJUnit4::class)
class SessionMidiReceiverTest {
    private fun bytes(vararg data: Int)=data.map{it.toByte()}.toByteArray()

    @Test fun staleSessionCannotDeliverQueuedEventsOrFlushNewConnection() {
        var current=true
        val queue=mutableListOf<()->Unit>();val events=mutableListOf<MidiEvent>();var resets=0
        val old=SessionMidiReceiver({current},{queue.add(it)},events::add,{resets++})
        val note=bytes(0x90,60,80)
        old.send(note,0,note.size,100)
        current=false
        old.flush()
        queue.toList().forEach{it()}
        assertTrue(events.isEmpty());assertEquals(0,resets)
        old.close()
    }

    @Test fun flushDropsPreflushEventsAndResetsRunningStatus() {
        val queue=mutableListOf<()->Unit>();val events=mutableListOf<MidiEvent>();var resets=0
        val receiver=SessionMidiReceiver({true},{queue.add(it)},events::add,{resets++})
        val first=bytes(0x90,60,80);receiver.send(first,0,first.size,100)
        receiver.flush()
        val staleRunningStatus=bytes(64,90);receiver.send(staleRunningStatus,0,staleRunningStatus.size,200)
        val fresh=bytes(0x91,65,100);receiver.send(fresh,0,fresh.size,300)
        queue.toList().forEach{it()}
        assertEquals(1,resets)
        assertEquals(listOf(MidiEvent.On(65,100,300,1)),events)
        receiver.close()
    }

    @Test fun fragmentedMessagesAreReassembledWithinOneConnection() {
        val queue=mutableListOf<()->Unit>();val events=mutableListOf<MidiEvent>()
        val receiver=SessionMidiReceiver({true},{queue.add(it)},events::add,{})
        val prefix=bytes(0x92,60);val suffix=bytes(88,64,0)
        receiver.send(prefix,0,prefix.size,100);receiver.send(suffix,0,suffix.size,200)
        queue.toList().forEach{it()}
        assertEquals(listOf(MidiEvent.On(60,88,200,2),MidiEvent.Off(64,0,200,2)),events)
        receiver.close()
    }

    @Test fun referenceOutputWithoutAConnectionDoesNotFeedInput() {
        val midi=BleMidi(InstrumentationRegistry.getInstrumentation().targetContext)
        val events=mutableListOf<MidiEvent>()
        try {
            midi.onEvent={events.add(it)}
            assertFalse(midi.state.value.canSend)
            for(pitch in listOf(-1,0,127,128))assertFalse(midi.sendReference(pitch,80,true))
            assertFalse(midi.sendReference(60,0,true))
            assertFalse(midi.sendReference(60,128,true))
            midi.stopReference()
            assertTrue(events.isEmpty());assertTrue(midi.state.value.held.isEmpty())
        } finally {midi.close()}
    }
}
