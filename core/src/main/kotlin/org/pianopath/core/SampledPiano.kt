package org.pianopath.core

import java.io.InputStream
import kotlin.math.*

data class PianoSample(val root: Int,val rate: Int,val pcm: ShortArray) {
    companion object {
        /** RIFF chunks may contain metadata and padding; do not assume a fixed WAV header size. */
        fun read(root: Int,input: InputStream): PianoSample {
            val bytes=input.use{it.readBytes()}
            fun u16(i: Int)=(bytes[i].toInt() and 255) or ((bytes[i+1].toInt() and 255) shl 8)
            fun u32(i: Int): Int {require(i+4<=bytes.size);return u16(i) or (u16(i+2) shl 16)}
            fun tag(i: Int)=String(bytes,i,4,Charsets.US_ASCII)
            require(bytes.size>=12 && tag(0)=="RIFF" && tag(8)=="WAVE"){"Invalid WAV"}
            var rate=0;var pcm: ShortArray?=null;var position=12
            while(position+8<=bytes.size) {
                val size=u32(position+4);val start=position+8
                require(size>=0 && size<=bytes.size-start){"Truncated WAV chunk"}
                when(tag(position)) {
                    "fmt " -> {require(size>=16 && u16(start)==1 && u16(start+2)==1 && u16(start+14)==16){"Expected mono PCM16"};rate=u32(start+4)}
                    "data" -> {require(size%2==0);pcm=ShortArray(size/2){u16(start+it*2).toShort()}}
                }
                position=start+size+(size and 1)
            }
            require(rate in 8000..192000 && pcm!=null && pcm.size>1){"Missing WAV data"}
            return PianoSample(root,rate,pcm)
        }
    }
}
/** Small offline acoustic sample bank. MIDI channels, sustain and repeated strikes remain independent. */
class SampledPiano(private val samples: List<PianoSample>,val sampleRate: Int=44100,private val maxVoices: Int=128) {
    init {require(samples.isNotEmpty() && maxVoices>0 && sampleRate>0)}
    private class Voice(val pitch: Int,val channel: Int,val sample: PianoSample,val step: Double,val gain: Double,
        var position: Double=0.0,var envelope: Double=1.0,var held: Boolean=true,var releasing: Boolean=false)
    private val voices=mutableListOf<Voice>()
    private val pedal=mutableSetOf<Int>()
    private val release=exp(-1.0/(sampleRate*.12))
    val activeVoiceCount: Int @Synchronized get()=voices.size
    @Synchronized fun accept(event: MidiEvent) {
        when(event) {
            is MidiEvent.On -> {
                if(event.velocity<=0){off(event.pitch,event.channel);return}
                if(event.pitch !in 0..127)return
                if(voices.size>=maxVoices)voices.removeAt(voices.indexOfFirst{it.releasing}.takeIf{it>=0} ?: 0)
                val sample=samples.minBy{abs(it.root-event.pitch)}
                voices.add(Voice(event.pitch,event.channel,sample,sample.rate.toDouble()/sampleRate*2.0.pow((event.pitch-sample.root)/12.0),
                    (event.velocity.coerceIn(1,127)/127.0).pow(1.5)*.65))
            }
            is MidiEvent.Off -> off(event.pitch,event.channel)
            is MidiEvent.Control -> when(event.controller) {
                64 -> if(event.value>=64)pedal.add(event.channel) else releasePedal(event.channel)
                121 -> releasePedal(event.channel)
                123 -> releaseChannel(event.channel)
                120 -> {voices.removeAll{it.channel==event.channel};pedal.remove(event.channel)}
            }
        }
    }
    private fun off(pitch: Int,channel: Int) {voices.firstOrNull{it.pitch==pitch && it.channel==channel && it.held}?.let{it.held=false;it.releasing=channel !in pedal}}
    private fun releasePedal(channel: Int) {pedal.remove(channel);voices.filter{it.channel==channel && !it.held}.forEach{it.releasing=true}}
    @Synchronized fun releaseChannel(channel: Int) {pedal.remove(channel);voices.filter{it.channel==channel}.forEach{it.held=false;it.releasing=true}}
    @Synchronized fun silence() {voices.clear();pedal.clear()}
    @Synchronized fun render(buffer: FloatArray,frames: Int=buffer.size) {
        require(frames in 0..buffer.size)
        java.util.Arrays.fill(buffer,0,frames,0f)
        for(v in voices) {
            for(i in 0 until frames) {
                val position=v.position.toInt()
                if(position>=v.sample.pcm.size-1)break
                val fraction=v.position-position
                val value=v.sample.pcm[position]*(1-fraction)+v.sample.pcm[position+1]*fraction
                buffer[i]+=(value/32768.0*v.gain*v.envelope).toFloat()
                v.position+=v.step
                if(v.releasing)v.envelope*=release
            }
        }
        voices.removeAll{it.envelope<.0001 || it.position>=it.sample.pcm.size-1}
        for(i in 0 until frames)buffer[i]=(buffer[i]/(1f+abs(buffer[i]))).coerceIn(-1f,1f)
    }
}
