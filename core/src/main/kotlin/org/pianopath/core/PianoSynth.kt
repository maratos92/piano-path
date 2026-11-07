package org.pianopath.core

import kotlin.math.*

/** Offline, velocity-sensitive additive piano-like synthesis. No samples, SoundFont, or network.
 * This is intentionally a compact synthesized tone, not a sampled acoustic grand piano.
 */
enum class SynthTone { MELLOW, BRIGHT, WARM, ELECTRIC }

class PianoSynth(val sampleRate: Int=44100,private val maxVoices: Int=64,private val tone: SynthTone=SynthTone.MELLOW) {
    init {require(sampleRate in 8000..192000 && maxVoices>0)}
    private class Voice(val pitch: Int,val channel: Int,val phases: DoubleArray,val steps: DoubleArray,
        val velocity: Double,val decay: Double,var envelope: Double=1.0,var attack: Double=0.0,
        var held: Boolean=true,var releasing: Boolean=false)
    private val voices=mutableListOf<Voice>()
    private val pedal=mutableSetOf<Int>()
    private val table=FloatArray(4096){sin(it*2*PI/4096).toFloat()}
    private val weights=when(tone) {
        SynthTone.MELLOW -> doubleArrayOf(1.0,.24,.09,.035,.015)
        SynthTone.BRIGHT -> doubleArrayOf(.85,.65,.35,.18,.09)
        SynthTone.WARM -> doubleArrayOf(1.0,.30,.11,.04,.01)
        SynthTone.ELECTRIC -> doubleArrayOf(.72,.51,.34,.16,.07)
    }
    private val releaseCoefficient=exp(-1.0/(sampleRate*.045))
    val activeVoiceCount: Int @Synchronized get()=voices.size
    @Synchronized fun accept(event: MidiEvent) {
        when(event) {
            is MidiEvent.On -> {
                if(event.velocity==0) {off(event.pitch,event.channel);return}
                if(event.pitch !in 0..127)return
                if(voices.size>=maxVoices)voices.removeAt(voices.indexOfFirst {it.releasing}.takeIf{it>=0} ?: 0)
                val frequency=440.0*2.0.pow((event.pitch-69)/12.0)
                val steps=DoubleArray(5){harmonic->frequency*(harmonic+1)*sqrt(1+.00006*(harmonic+1).toDouble().pow(2))*4096/sampleRate}
                voices.add(Voice(event.pitch,event.channel,DoubleArray(5),steps,
                    (event.velocity.coerceIn(1,127)/127.0).pow(1.4)*.19,
                    exp(-1.0/(sampleRate*(3.5-(event.pitch-21).coerceIn(0,87)/87.0*2.1)))))
            }
            is MidiEvent.Off -> off(event.pitch,event.channel)
            is MidiEvent.Control -> when(event.controller) {
                64 -> if(event.value>=64)pedal.add(event.channel) else {
                    pedal.remove(event.channel);voices.filter {it.channel==event.channel&&!it.held}.forEach {it.releasing=true}
                }
                120,123 -> {pedal.remove(event.channel);voices.filter {it.channel==event.channel}.forEach {it.held=false;it.releasing=true}}
                121 -> {pedal.remove(event.channel);voices.filter {it.channel==event.channel&&!it.held}.forEach {it.releasing=true}}
            }
        }
    }
    private fun off(pitch: Int,channel: Int) {
        voices.firstOrNull {it.pitch==pitch&&it.channel==channel&&it.held}?.let {
            it.held=false;if(channel !in pedal)it.releasing=true
        }
    }
    @Synchronized fun silence() {pedal.clear();voices.clear()}
    @Synchronized fun releaseChannel(channel: Int) {
        pedal.remove(channel);voices.filter {it.channel==channel}.forEach {it.held=false;it.releasing=true}
    }
    @Synchronized fun render(buffer: FloatArray,frames: Int=buffer.size) {
        require(frames in 0..buffer.size)
        for(frame in 0 until frames) {
            var sum=0.0
            for(v in voices) {
                v.attack=min(1.0,v.attack+1.0/(sampleRate*.003))
                var sample=0.0
                for(h in weights.indices) {
                    if(v.steps[h]<2048)sample+=table[v.phases[h].toInt() and 4095]*weights[h]
                    v.phases[h]=(v.phases[h]+v.steps[h])%4096
                }
                sum+=sample*v.velocity*v.envelope*v.attack
                v.envelope*=if(v.releasing)releaseCoefficient else v.decay
            }
            buffer[frame]=(sum/(1+abs(sum))).toFloat()
        }
        voices.removeAll {it.envelope<.0001}
    }
}
