package org.pianopath.core

/** Physical piano geometry in white-key units. Shared by touch hit testing and the piano roll. */
object PianoLayout {
    const val FIRST = 21
    const val LAST = 108
    const val WHITE_COUNT = 52
    data class Key(val pitch: Int, val left: Double, val width: Double, val black: Boolean)
    private val geometry=KeyboardGeometry(FIRST..LAST)
    fun isBlack(pitch: Int)=KeyboardGeometry.isBlack(pitch)
    val keys: List<Key> = geometry.keys
    val whiteKeys=geometry.whiteKeys
    val blackKeys=geometry.blackKeys
    fun key(pitch: Int): Key?=geometry.key(pitch)
    fun hit(unitX: Double,normalizedY: Double): Int?=geometry.hit(unitX,normalizedY)
    fun unitX(screenFraction: Double,zoom: Double,pan: Double)=geometry.unitX(screenFraction,zoom,pan)
    fun screenX(unit: Double,zoom: Double,pan: Double)=geometry.screenX(unit,zoom,pan)
}

data class PlaybackNoteEvent(val beat: Double,val pitch: Int,val down: Boolean,val channel: Int,val velocity: Int=80)
object PlaybackPlan {
    /** Reconstruct held voices at a seek/resume boundary, retaining channel and velocity. */
    fun activeBefore(events: List<PlaybackNoteEvent>, nextEvent: Int): List<PlaybackNoteEvent> {
        val active=mutableMapOf<Pair<Int,Int>,java.util.ArrayDeque<PlaybackNoteEvent>>()
        for(index in 0 until nextEvent.coerceIn(0,events.size)) {
            val e=events[index];val key=e.channel to e.pitch
            if(e.down)active.getOrPut(key){java.util.ArrayDeque()}.addLast(e)
            else active[key]?.pollFirst()
        }
        return active.values.flatMap {it.toList()}
    }
    /** A diatonic upper neighbour, based on the score's key signature. */
    private fun upper(pitch: Int,fifths: Int): Int {
        val tonic=Math.floorMod(fifths*7,12)
        return (1..3).map {pitch+it}.firstOrNull {Math.floorMod(it-tonic,12) in setOf(0,2,4,5,7,9,11)} ?: pitch+2
    }
    fun create(score: Score,start: Double,end: Double,hand: Hand=Hand.BOTH): List<PlaybackNoteEvent> {
        require(start>=0 && end>start && end<=score.endBeat+.0001)
        val events=mutableListOf<PlaybackNoteEvent>()
        val groups=score.graceNotes.filter {it.beat>=start-.0001 && it.beat<end-.0001 &&
            (hand==Hand.BOTH || if(it.staff>1)hand==Hand.LEFT else hand==Hand.RIGHT)}
            .groupBy {Triple(it.beat,it.staff,it.voice)}
        val shifted=mutableMapOf<Triple<Double,Int,String>,Double>()
        groups.forEach { (key,notes) ->
            val total=(notes.size*.12).coerceAtMost(.6)
            val first=(key.first-total).coerceAtLeast(start)
            val span=(key.first-first).coerceAtLeast(0.0)
            val step=if(span>0) span/notes.size else .10
            val delay=(notes.size*step-span).coerceAtLeast(0.0)
            if(delay>0)shifted[key]=delay
            notes.forEachIndexed { index,n ->
                val at=(first+index*step).coerceAtMost(end-.001)
                val off=(at+step*.85).coerceAtMost(end)
                if(off>at) {
                    events.add(PlaybackNoteEvent(at,n.midi,true,n.staff.coerceIn(1,15),68))
                    events.add(PlaybackNoteEvent(off,n.midi,false,n.staff.coerceIn(1,15)))
                }
            }
        }
        score.selected(start,end,hand).forEach {note ->
            val channel=note.staff.coerceIn(1,15) // Touch keyboard uses channel 0.
            val begin=note.beat+(shifted[Triple(note.beat,note.staff,note.voice)]?:0.0)
            val finish=minOf(note.beat+note.duration,end)
            if(begin>=finish)return@forEach
            val ornament=score.ornaments.firstOrNull {it.noteId==note.id}?.kind
            val neighbour=upper(note.midi,score.fifths).coerceIn(0,127)
            val count=when(ornament) {
                OrnamentKind.TRILL -> (note.duration/.16).toInt().coerceIn(4,20)
                OrnamentKind.INVERTED_MORDENT -> 3
                null -> 1
            }
            val short=if(count==1)finish-begin else (finish-begin)/count
            repeat(count) {index ->
                val pitch=if(index%2==1)neighbour else note.midi
                val at=begin+short*index
                val release=if(index==count-1)finish else (at+short*.93).coerceAtMost(finish)
                events.add(PlaybackNoteEvent(at,pitch,true,channel))
                events.add(PlaybackNoteEvent(release,pitch,false,channel))
            }
        }
        return events.sortedWith(compareBy({it.beat},{if(it.down)1 else 0},{it.pitch}))
    }
}

data class RollBar(val note: ScoreNote,val top: Double,val bottom: Double)
class PianoRoll(score: Score,start: Double,end: Double,hand: Hand=Hand.BOTH) {
    private val endBeat=end
    private val notes=score.selected(start,end,hand).sortedBy {it.beat}
    private val maxDuration=notes.maxOfOrNull {it.duration} ?: 0.0
    fun visible(position: Double,lookAhead: Double=8.0): List<RollBar> {
        require(lookAhead>0)
        // A long tied note may begin well before the visible window.
        var low=0;var high=notes.size
        while(low<high) {val mid=(low+high)/2;if(notes[mid].beat<position-maxDuration)low=mid+1 else high=mid}
        val result=mutableListOf<RollBar>()
        for(i in low until notes.size) {
            val n=notes[i]
            if(n.beat>position+lookAhead)break
            val stop=minOf(n.beat+n.duration,endBeat)
            if(stop<=position)continue
            result.add(RollBar(n,(1-(stop-position)/lookAhead).coerceIn(0.0,1.0),
                (1-(n.beat-position)/lookAhead).coerceIn(0.0,1.0)))
        }
        return result
    }
}
