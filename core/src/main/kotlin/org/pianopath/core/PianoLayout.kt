package org.pianopath.core

/** Physical piano geometry in white-key units. Shared by touch hit testing and the piano roll. */
object PianoLayout {
    const val FIRST = 21
    const val LAST = 108
    const val WHITE_COUNT = 52
    data class Key(val pitch: Int, val left: Double, val width: Double, val black: Boolean)
    fun isBlack(pitch: Int) = Math.floorMod(pitch,12) in listOf(1,3,6,8,10)
    val keys: List<Key> = buildList {
        var white = 0
        for(pitch in FIRST..LAST) {
            val black = isBlack(pitch)
            add(Key(pitch,if(black) white-.31 else white.toDouble(),if(black) .62 else 1.0,black))
            if(!black) white++
        }
    }
    val whiteKeys = keys.filter { !it.black }
    val blackKeys = keys.filter { it.black }
    fun key(pitch: Int): Key? = keys.getOrNull(pitch-FIRST)
    fun hit(unitX: Double, normalizedY: Double): Int? {
        if(unitX<0 || unitX>=WHITE_COUNT || normalizedY !in 0.0..1.0) return null
        if(normalizedY<.62) blackKeys.firstOrNull { unitX>=it.left && unitX<it.left+it.width }?.let {return it.pitch}
        return whiteKeys.firstOrNull { unitX>=it.left && unitX<it.left+it.width }?.pitch
    }
    fun unitX(screenFraction: Double,zoom: Double,pan: Double): Double {
        require(zoom>=1.0)
        return (screenFraction+pan.coerceIn(0.0,1.0)*(zoom-1))*WHITE_COUNT/zoom
    }
    fun screenX(unit: Double,zoom: Double,pan: Double) = unit/WHITE_COUNT*zoom-pan.coerceIn(0.0,1.0)*(zoom-1)
}

data class PlaybackNoteEvent(val beat: Double,val pitch: Int,val down: Boolean,val channel: Int,val velocity: Int=80)
object PlaybackPlan {
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
