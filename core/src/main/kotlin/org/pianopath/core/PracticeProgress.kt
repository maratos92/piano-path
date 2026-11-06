package org.pianopath.core

import java.time.*

/** A clock for activity duration, independent of score-following phase and wall-clock changes. */
class PracticeClock {
    private var start: Long?=null
    private var paused: Long?=null
    private var excluded=0L
    fun begin(now: Long){start=now;paused=null;excluded=0}
    fun pause(now: Long){if(start!=null && paused==null)paused=now}
    fun resume(now: Long){paused?.let{excluded+=(now-it).coerceAtLeast(0);paused=null}}
    fun millis(now: Long): Long = start?.let {((minOf(now,paused ?: now)-it-excluded)/1_000_000).coerceAtLeast(0)} ?: 0
}
data class PracticeEntry(val id: String,val date: Long,val title: String,val category: String,
    val scoreKey: String="",val startBeat: Double=0.0,val endBeat: Double=0.0,val bpm: Double=80.0,
    val mode: String="",val hand: String="BOTH",val tolerance: Double=180.0,val latency: Double=0.0,
    val source: String="MIDI",val activeMs: Long=0,val pitch: Double?=null,val rhythm: Double?=null,
    val overall: Double?=null,val expected: Int=0,val missing: Int=0,val extra: Int=0,
    val weakMeasure: Int?=null,val reviewFile: String?=null,val scoringVersion: Int=1,
    val correct: Int=0,val total: Int=0,val roundId: String="",val exercise: String="",val onTimeCorrect: Int?=null) {
    val onTime get()=onTimeCorrect?.let{if(expected>0)100.0*it/expected else null}
    /** Only genuinely comparable trials share a trend; device calibration/input is part of context. */
    val comparisonKey get()=listOf(scoreKey,startBeat,endBeat,bpm,mode,hand,tolerance,latency,source,scoringVersion,exercise).joinToString("|")
}
data class PracticeTrend(val latest: PracticeEntry,val previous: PracticeEntry?,val trials: Int) {
    val pitchChange get()=latest.pitch?.let {p->previous?.pitch?.let{p-it}}
    val rhythmChange get()=latest.rhythm?.let {p->previous?.rhythm?.let{p-it}}
}
object PracticeInsights {
    fun day(date: Long,zone: ZoneId=ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(date).atZone(zone).toLocalDate()
    fun activeToday(entries: List<PracticeEntry>,now: Long,zone: ZoneId=ZoneId.systemDefault()): Long = entries.filter{day(it.date,zone)==day(now,zone)}.sumOf{it.activeMs.coerceAtLeast(0)}
    fun trends(entries: List<PracticeEntry>): List<PracticeTrend> = entries.filter{it.category!="Ear training" && it.pitch!=null}.groupBy{it.comparisonKey}.values.map{items->
        val sorted=items.sortedByDescending{it.date};PracticeTrend(sorted.first(),sorted.getOrNull(1),sorted.size)
    }.sortedByDescending{it.latest.date}
    /** Latest result per passage/context prevents stale mistakes overwhelming later improvement. */
    fun queue(entries: List<PracticeEntry>): List<PracticeEntry> = trends(entries).map{it.latest}
        .filter{it.reviewFile!=null && it.expected>0 && (it.missing>0 || it.extra>0 || (it.pitch ?: 100.0)<95 || (it.rhythm ?: 100.0)<90)}
        .sortedWith(compareBy<PracticeEntry>{it.overall ?: it.pitch ?: 100.0}.thenByDescending{it.date}).take(3)
}
