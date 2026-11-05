package org.pianopath.core

data class PlayedAttack(val pitch: Int,val channel: Int,val startedNs: Long,val durationMs: Long?=null)
data class NotationPitch(val writtenPitch: Int,val octaves: Int,val diatonic: Int)

/** A display heuristic only: never quantizes, merges, or changes the source attacks. */
fun groupPlayedAttacks(notes: List<PlayedAttack>,windowMs: Long=60): List<List<PlayedAttack>> {
    require(windowMs in 0..1000)
    val groups=mutableListOf<MutableList<PlayedAttack>>()
    notes.forEach {note->
        val current=groups.lastOrNull()
        val offset=current?.let{note.startedNs-it.first().startedNs}
        if(current==null || offset==null || offset !in 0..windowMs*1_000_000 || current.any{it.pitch==note.pitch && it.channel==note.channel})
            groups.add(mutableListOf(note))
        else current.add(note)
    }
    return groups
}

/** Grand-staff display range; octave marks retain extreme pitches without clipping or clamping. */
fun pitchForNotation(pitch: Int): NotationPitch {
    require(pitch in 0..127)
    var written=pitch
    while(written<36)written+=12
    while(written>84)written-=12
    val step=written/12*7+intArrayOf(0,0,1,1,2,3,3,4,4,5,5,6)[written%12]
    return NotationPitch(written,(pitch-written)/12,step)
}

/** Staff bottom is 0 and top is 8; spaces outside the staff need no extra ledger. */
fun ledgerStepsFromStaffBottom(step: Int): List<Int> = when {
    step<0->(-2 downTo step step 2).toList()
    step>8->(10..step step 2).toList()
    else->emptyList()
}
