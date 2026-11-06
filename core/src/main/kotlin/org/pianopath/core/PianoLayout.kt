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
    fun create(score: Score,start: Double,end: Double,hand: Hand=Hand.BOTH): List<PlaybackNoteEvent> {
        require(start>=0 && end>start && end<=score.endBeat+.0001)
        return score.selected(start,end,hand).flatMap { note ->
            val channel=note.staff.coerceIn(1,15) // Channel 0 is reserved for the touch keyboard.
            listOf(PlaybackNoteEvent(note.beat,note.midi,true,channel),
                PlaybackNoteEvent(minOf(note.beat+note.duration,end),note.midi,false,channel))
        }.sortedWith(compareBy({it.beat},{if(it.down)1 else 0},{it.pitch}))
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
