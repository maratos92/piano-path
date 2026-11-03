package org.pianopath.core

sealed class MidiEvent(open val timestamp: Long, open val channel: Int) {
    data class On(val pitch: Int, val velocity: Int, override val timestamp: Long, override val channel: Int = 0): MidiEvent(timestamp,channel)
    data class Off(val pitch: Int, val velocity: Int, override val timestamp: Long, override val channel: Int = 0): MidiEvent(timestamp,channel)
    data class Control(val controller: Int, val value: Int, override val timestamp: Long, override val channel: Int = 0): MidiEvent(timestamp,channel)
}
/** Stateful MIDI 1.0 byte-stream decoder. BLE framing is decoded by Android's MIDI service. */
class MidiParser(private val emit: (MidiEvent) -> Unit) {
    private var status = 0
    private var count = 0
    private val data = IntArray(2)
    private var sysex = false
    @Synchronized fun reset() { status = 0; count = 0; sysex = false }
    @Synchronized fun feed(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset, timestamp: Long) {
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size)
        for (i in offset until offset + length) {
            val b = bytes[i].toInt() and 255
            if (b >= 0xf8) continue // Real-time messages can interrupt any message, even SysEx.
            if (b == 0xf0) { sysex = true; status = 0; count = 0; continue }
            if (b == 0xf7) { sysex = false; continue }
            if (sysex) continue
            if (b and 128 != 0) { status = if (b < 0xf0) b else 0; count = 0; continue }
            if (status == 0) continue
            data[count++] = b
            val needed = if (status and 0xf0 in listOf(0xc0,0xd0)) 1 else 2
            if (count == needed) {
                val ch = status and 15
                when (status and 0xf0) {
                    0x80 -> emit(MidiEvent.Off(data[0], data[1],timestamp,ch))
                    0x90 -> emit(if (data[1] == 0) MidiEvent.Off(data[0],0,timestamp,ch) else MidiEvent.On(data[0],data[1],timestamp,ch))
                    0xb0 -> emit(MidiEvent.Control(data[0],data[1],timestamp,ch))
                }
                count = 0
            }
        }
    }
}
class KeyboardState {
    val held = mutableMapOf<Pair<Int,Int>, Int>()
    val sounding = mutableSetOf<Pair<Int,Int>>()
    val sustain = mutableSetOf<Int>()
    fun accept(e: MidiEvent) {
        when(e) {
            is MidiEvent.On -> { held[e.channel to e.pitch] = e.velocity; sounding.add(e.channel to e.pitch) }
            is MidiEvent.Off -> { held.remove(e.channel to e.pitch); if(e.channel !in sustain) sounding.remove(e.channel to e.pitch) }
            is MidiEvent.Control -> when(e.controller) {
                64 -> if(e.value >= 64) sustain.add(e.channel) else { sustain.remove(e.channel); sounding.removeAll { it.first == e.channel && it !in held } }
                120,123 -> { held.keys.removeAll { it.first == e.channel }; sounding.removeAll { it.first == e.channel }; sustain.remove(e.channel) }
                121 -> { sustain.remove(e.channel); sounding.removeAll { it.first == e.channel && it !in held } }
            }
        }
    }
    fun reset() { held.clear(); sounding.clear(); sustain.clear() }
}
