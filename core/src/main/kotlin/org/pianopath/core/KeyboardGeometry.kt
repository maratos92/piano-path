package org.pianopath.core

/** Absolute MIDI geometry in white-key units; no pitch transposition or assessment filtering. */
class KeyboardGeometry(val range: IntRange=21..108) {
    init {require(!range.isEmpty() && range.first in 0..127 && range.last in 0..127)}

    private fun whiteBefore(pitch: Int): Double = (pitch/12*7+intArrayOf(0,1,1,2,2,3,4,4,5,5,6,6)[pitch%12]).toDouble()
    private val singleBlack=range.first==range.last && isBlack(range.first)
    // Cropping at a black endpoint's centre keeps the included white keys contiguous below it.
    private val origin=whiteBefore(range.first)-if(singleBlack).31 else 0.0
    private val end=if(singleBlack)origin+.62 else whiteBefore(range.last)+if(isBlack(range.last))0.0 else 1.0
    val whiteSpan: Double=end-origin
    val keys: List<PianoLayout.Key> = range.map {pitch->
        val black=isBlack(pitch)
        val naturalLeft=whiteBefore(pitch)-if(black).31 else 0.0
        val left=(naturalLeft-origin).coerceAtLeast(0.0)
        val right=(naturalLeft-origin+if(black).62 else 1.0).coerceAtMost(whiteSpan)
        PianoLayout.Key(pitch,left,right-left,black)
    }
    val whiteKeys=keys.filterNot{it.black}
    val blackKeys=keys.filter{it.black}
    fun key(pitch: Int): PianoLayout.Key? = keys.getOrNull(pitch-range.first)

    fun hit(unitX: Double,normalizedY: Double): Int? {
        if(!unitX.isFinite() || unitX<0 || unitX>=whiteSpan || normalizedY !in 0.0..1.0)return null
        if(normalizedY<.62)blackKeys.firstOrNull{unitX>=it.left && unitX<it.left+it.width}?.let{return it.pitch}
        return whiteKeys.firstOrNull{unitX>=it.left && unitX<it.left+it.width}?.pitch
    }
    fun unitX(screenFraction: Double,zoom: Double,pan: Double): Double {
        require(zoom.isFinite() && zoom>=1.0)
        return (screenFraction+pan.coerceIn(0.0,1.0)*(zoom-1))*whiteSpan/zoom
    }
    fun screenX(unit: Double,zoom: Double,pan: Double): Double {
        require(zoom.isFinite() && zoom>=1.0)
        return unit/whiteSpan*zoom-pan.coerceIn(0.0,1.0)*(zoom-1)
    }
    companion object {
        fun isBlack(pitch: Int)=Math.floorMod(pitch,12) in listOf(1,3,6,8,10)
    }
}
