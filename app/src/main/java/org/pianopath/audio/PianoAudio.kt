package org.pianopath.audio

import android.content.Context
import android.net.Uri
import android.media.*
import android.os.Handler
import android.os.Looper
import org.pianopath.core.*
import java.util.concurrent.atomic.AtomicBoolean

enum class PianoSound(val label: String) {
    GRAND("Sampled grand"), MELLOW("Mellow synth"), BRIGHT("Bright synth"),
    WARM("Warm keys"), ELECTRIC("Electric keys"), CUSTOM("Imported WAV pack")
}

/** PCM output is independent from assessment. Listening never feeds generated notes into the scorer. */
class PianoAudio(context: Context,private val interrupted: (String)->Unit) {
    private val manager=context.getSystemService(AudioManager::class.java)
    // Both transports share read-only PCM samples, with separate voices and pedal state.
    private val samples by lazy { (21..108 step 3).map { pitch -> PianoSample.read(pitch,context.assets.open("piano/$pitch.wav")) } }
    private val grand by lazy { SampledPiano(samples) }
    private val mellow by lazy { PianoSynth(tone=SynthTone.MELLOW) }
    private val bright by lazy { PianoSynth(tone=SynthTone.BRIGHT) }
    private val warm by lazy { PianoSynth(tone=SynthTone.WARM) }
    private val electric by lazy { PianoSynth(tone=SynthTone.ELECTRIC) }
    private val recordingGrand by lazy { SampledPiano(samples) }
    private val recordingMellow by lazy { PianoSynth(tone=SynthTone.MELLOW) }
    private val recordingBright by lazy { PianoSynth(tone=SynthTone.BRIGHT) }
    private val recordingWarm by lazy { PianoSynth(tone=SynthTone.WARM) }
    private val recordingElectric by lazy { PianoSynth(tone=SynthTone.ELECTRIC) }
    private val soundPacks=SoundPackStore(context.applicationContext)
    @Volatile private var customBanks: Pair<SampledPiano,SampledPiano>?=null
    @Volatile private var sound=PianoSound.GRAND
    @Volatile private var recordingPlayback=false
    fun selectSound(next: PianoSound) {if(sound!=next){silence();sound=next}}
    fun hasCustomPack()=soundPacks.exists()
    /** Call from a background coroutine: decoding may take time on large packs. */
    fun installCustomPack(uri: Uri): String {
        val loaded=soundPacks.install(uri)
        val pair=SampledPiano(loaded.samples) to SampledPiano(loaded.samples)
        if(sound==PianoSound.CUSTOM)silence()
        customBanks=pair
        return loaded.label
    }
    fun loadStoredPack(): Boolean {
        val loaded=soundPacks.load() ?: return false
        customBanks=SampledPiano(loaded.samples) to SampledPiano(loaded.samples)
        return true
    }
    fun removeCustomPack() {
        if(sound==PianoSound.CUSTOM)selectSound(PianoSound.GRAND)
        customBanks=null
        soundPacks.delete()
    }
    private fun render(buffer: FloatArray,recordingBuffer: FloatArray) {
        when(sound) {
            PianoSound.GRAND->{grand.render(buffer);recordingGrand.render(recordingBuffer)}
            PianoSound.MELLOW->{mellow.render(buffer);recordingMellow.render(recordingBuffer)}
            PianoSound.BRIGHT->{bright.render(buffer);recordingBright.render(recordingBuffer)}
            PianoSound.WARM->{warm.render(buffer);recordingWarm.render(recordingBuffer)}
            PianoSound.ELECTRIC->{electric.render(buffer);recordingElectric.render(recordingBuffer)}
            PianoSound.CUSTOM->{val bank=customBanks
                if(bank!=null){bank.first.render(buffer);bank.second.render(recordingBuffer)}
                else{grand.render(buffer);recordingGrand.render(recordingBuffer)}}
        }
        for(i in buffer.indices)buffer[i]=(buffer[i]+recordingBuffer[i]).coerceIn(-1f,1f)
        clicker.mix(buffer)
        for(i in buffer.indices)buffer[i]=buffer[i].coerceIn(-1f,1f)
    }
    private fun voices()=when(sound) {
        PianoSound.GRAND->grand.activeVoiceCount+recordingGrand.activeVoiceCount
        PianoSound.MELLOW->mellow.activeVoiceCount+recordingMellow.activeVoiceCount
        PianoSound.BRIGHT->bright.activeVoiceCount+recordingBright.activeVoiceCount
        PianoSound.WARM->warm.activeVoiceCount+recordingWarm.activeVoiceCount
        PianoSound.ELECTRIC->electric.activeVoiceCount+recordingElectric.activeVoiceCount
        PianoSound.CUSTOM->customBanks?.let{it.first.activeVoiceCount+it.second.activeVoiceCount}
            ?: (grand.activeVoiceCount+recordingGrand.activeVoiceCount)
    }
    private val clicker=MetronomeClick()
    fun setMetronomeVolume(value: Float){clicker.volume=value.coerceIn(0f,1f)}
    fun metronomeClick(downbeat: Boolean) {if(start())clicker.trigger(downbeat)}
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
                    val recordingBuffer=FloatArray(buffer.size)
                    track.play()
                    var idleFrames=0
                    while(alive.get()) {
                        render(buffer,recordingBuffer)
                        idleFrames=if(voices()==0 && !clicker.active && !recordingPlayback)idleFrames+buffer.size else 0
                        if(idleFrames>=44100*2) {
                            idleFrames=0
                            handler.post {if(output?.track===track && voices()==0 && !clicker.active && !recordingPlayback)stop()}
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
    fun accept(event: MidiEvent) {if(output!=null)when(sound) {
        PianoSound.GRAND->grand.accept(event)
        PianoSound.MELLOW->mellow.accept(event)
        PianoSound.BRIGHT->bright.accept(event)
        PianoSound.WARM->warm.accept(event)
        PianoSound.ELECTRIC->electric.accept(event)
        PianoSound.CUSTOM->(customBanks?.first ?: grand).accept(event)
    }}
    /** Replay never enters the live input/scoring path, even on MIDI channel zero. */
    fun acceptRecording(event: MidiEvent) {
        if(!start())return
        recordingPlayback=true
        when(sound) {
        PianoSound.GRAND->recordingGrand.accept(event)
        PianoSound.MELLOW->recordingMellow.accept(event)
        PianoSound.BRIGHT->recordingBright.accept(event)
        PianoSound.WARM->recordingWarm.accept(event)
        PianoSound.ELECTRIC->recordingElectric.accept(event)
        PianoSound.CUSTOM->(customBanks?.second ?: recordingGrand).accept(event)
    }}
    fun silenceRecording() {recordingPlayback=false;when(sound) {
        PianoSound.GRAND->recordingGrand.silence()
        PianoSound.MELLOW->recordingMellow.silence()
        PianoSound.BRIGHT->recordingBright.silence()
        PianoSound.WARM->recordingWarm.silence()
        PianoSound.ELECTRIC->recordingElectric.silence()
        PianoSound.CUSTOM->(customBanks?.second ?: recordingGrand).silence()
    }}
    fun releaseTouch()=when(sound) {
        PianoSound.GRAND->grand.releaseChannel(0)
        PianoSound.MELLOW->mellow.releaseChannel(0)
        PianoSound.BRIGHT->bright.releaseChannel(0)
        PianoSound.WARM->warm.releaseChannel(0)
        PianoSound.ELECTRIC->electric.releaseChannel(0)
        PianoSound.CUSTOM->(customBanks?.first ?: grand).releaseChannel(0)
    }
    fun releasePlayback() {(1..15).forEach { channel -> when(sound) {
        PianoSound.GRAND->grand.releaseChannel(channel)
        PianoSound.MELLOW->mellow.releaseChannel(channel)
        PianoSound.BRIGHT->bright.releaseChannel(channel)
        PianoSound.WARM->warm.releaseChannel(channel)
        PianoSound.ELECTRIC->electric.releaseChannel(channel)
        PianoSound.CUSTOM->(customBanks?.first ?: grand).releaseChannel(channel)
    } }}
    fun silence() {
        clicker.silence()
        silenceRecording()
        when(sound) {
            PianoSound.GRAND->grand.silence()
            PianoSound.MELLOW->mellow.silence()
            PianoSound.BRIGHT->bright.silence()
            PianoSound.WARM->warm.silence()
            PianoSound.ELECTRIC->electric.silence()
            PianoSound.CUSTOM->(customBanks?.first ?: grand).silence()
        }
    }
    fun stop() {
        val previous=output;output=null
        previous?.alive?.set(false)
        try {previous?.track?.pause()}catch(_: Exception){}
        previous?.thread?.interrupt()
        silence();manager?.abandonAudioFocusRequest(focus)
    }
}
