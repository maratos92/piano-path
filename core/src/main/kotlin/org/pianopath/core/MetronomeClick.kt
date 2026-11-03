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
    private var frame=0
    private var length=0
    private var accent=false
    @Synchronized fun trigger(downbeat: Boolean) {
        accent=downbeat
        frame=0
        length=(sampleRate*.095).toInt()
    }
    val active: Boolean @Synchronized get()=frame<length
    @Synchronized fun silence() {frame=0;length=0}
    @Synchronized fun mix(buffer: FloatArray) {
        for(i in buffer.indices) {
            if(frame>=length)break
            val t=frame.toDouble()/sampleRate
            // A slightly lower and louder downbeat; a quick attack and rounded decay.
            val freq=if(accent)790.0 else 1080.0
            val attack=(t/.0015).coerceIn(0.0,1.0)
            val envelope=attack*exp(-t*68)
            val tap=(sin(2*PI*freq*t)+.28*sin(2*PI*freq*1.79*t))*envelope*(if(accent).23 else .15)
            buffer[i]=(buffer[i]+tap.toFloat()).coerceIn(-1f,1f)
            frame++
        }
    }
}
