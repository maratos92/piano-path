package org.pianopath.audio

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import org.pianopath.core.*
import java.util.concurrent.atomic.AtomicBoolean

/** PCM output is independent from assessment. Listening never feeds generated notes into the scorer. */
class PianoAudio(context: Context,private val interrupted: (String)->Unit) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val synth=PianoSynth()
    private val handler=Handler(Looper.getMainLooper())
    private data class Output(val track: AudioTrack,val alive: AtomicBoolean,val thread: Thread)
    private var output: Output?=null
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                stop();interrupted("Sound interrupted by another app")
            }
        },handler).build()
    fun start(): Boolean {
        if(output!=null)return true
        if(manager?.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            interrupted("Audio is busy. Try Listen or a key again.");return false
        }
        return try {
            val size=AudioTrack.getMinBufferSize(44100,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_FLOAT).coerceAtLeast(4096)
            val track=AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(size)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build()
            if(track.state!=AudioTrack.STATE_INITIALIZED){track.release();error("PCM audio could not initialize")}
            val alive=AtomicBoolean(true)
            val thread=Thread({
                try {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
                    val buffer=FloatArray(256)
                    track.play()
                    var idleFrames=0
                    while(alive.get()) {
                        synth.render(buffer)
                        idleFrames=if(synth.activeVoiceCount==0)idleFrames+buffer.size else 0
                        if(idleFrames>=synth.sampleRate*2) {
                            idleFrames=0
                            handler.post {if(output?.track===track && synth.activeVoiceCount==0)stop()}
                        }
                        var offset=0
                        while(offset<buffer.size&&alive.get()) {
                            val wrote=track.write(buffer,offset,buffer.size-offset,AudioTrack.WRITE_BLOCKING)
                            check(wrote>=0){"Audio output error $wrote"}
                            if(wrote==0)Thread.yield() else offset+=wrote
                        }
                    }
                } catch(e: Exception) {
                    if(alive.get())handler.post {if(output?.track===track){stop();interrupted("Sound unavailable: ${e.message}")}}
                } finally {try {track.stop()}catch(_: Exception){};track.release()}
            },"PianoPath-PCM").apply {isDaemon=true}
            output=Output(track,alive,thread);thread.start();true
        } catch(e: Exception) {
            manager?.abandonAudioFocusRequest(focus);interrupted("Sound unavailable: ${e.message}");false
        }
    }
    fun accept(event: MidiEvent) {if(output!=null)synth.accept(event)}
    fun releaseTouch()=synth.releaseChannel(0)
    fun releasePlayback() {(1..15).forEach(synth::releaseChannel)}
    fun silence()=synth.silence()
    fun stop() {
        val previous=output;output=null
        previous?.alive?.set(false)
        try {previous?.track?.pause()}catch(_: Exception){}
        previous?.thread?.interrupt()
        synth.silence();manager?.abandonAudioFocusRequest(focus)
    }
}
