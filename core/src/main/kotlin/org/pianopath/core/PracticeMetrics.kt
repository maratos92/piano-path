package org.pianopath.core

import kotlin.math.abs

/** Explicit counts, independent of the legacy blended onset/release score. */
data class PracticeMetrics(val matched: Int,val expected: Int,val extra: Int,val onTimeCorrect: Int?) {
    val accuracy get()=if(expected+extra>0)100.0*matched/(expected+extra) else null
    val onTime get()=onTimeCorrect?.let{if(expected>0)100.0*it/expected else null}
    companion object {
        fun from(result: SessionResult,mode: PracticeMode,toleranceMs: Double): PracticeMetrics = PracticeMetrics(
            result.correct,result.expected,result.extra,
            if(mode==PracticeMode.WAIT || mode==PracticeMode.FREE)null else result.details.count{!it.missing && it.onsetMs?.let{ms->abs(ms)<=toleranceMs}==true})
    }
}
fun progressCategory(entry: PracticeEntry): String = when(entry.category){
    "Score","Imported score"->"Pieces"
    "Scales"->"Scales"
    "Sight reading"->"Sight reading"
    "Ear training"->"Listening"
    else->"Other"
}
