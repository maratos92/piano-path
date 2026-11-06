package org.pianopath.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Short, damped two-partial wooden tap. Produces PCM inside the existing audio stream,
 * avoiding Android's telephone-like ToneGenerator beeps for ordinary practice.
 */
class MetronomeClick(private val sampleRate: Int = 44100) {
    init { require(sampleRate in 8000..192000) }
    @Volatile var volume: Float=.5f
        set(value){field=if(value.isFinite())value.coerceIn(0f,1f) else 0f}
    private var noise=918273
    private var filtered=0.0
    private var frame=0
    private var length=0
    private var accent=false
    @Synchronized fun trigger(downbeat: Boolean) {
        accent=downbeat
        frame=0
        length=(sampleRate*.06).toInt();filtered=0.0
    }
    val active: Boolean @Synchronized get()=frame<length
    @Synchronized fun silence() {frame=0;length=0}
    @Synchronized fun mix(buffer: FloatArray) {
        for(i in buffer.indices) {
            if(frame>=length)break
            val t=frame.toDouble()/sampleRate
            // A broad-band struck-wood transient: no sustained oscillator/beep.
            noise=noise*1664525+1013904223
            val white=(noise ushr 8)/8388607.5-1.0
            filtered+=.34*(white-filtered)
            val envelope=(t/.0006).coerceIn(0.0,1.0)*exp(-t*105)
            val body=sin(2*PI*(if(accent)510.0 else 650.0)*t)*exp(-t*160)
            val tap=(filtered*.72+body*.22)*envelope*(if(accent).8 else .6)*volume.coerceIn(0f,1f)
            buffer[i]=(buffer[i]+tap.toFloat()).coerceIn(-1f,1f)
            frame++
        }
    }
}
