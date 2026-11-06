package org.pianopath.core

import kotlin.random.Random

/** Random clusters are an advanced option; three-note default uses major/minor root-position triads. */
fun earExercise(low: Int,high: Int,voices: Int,triads: Boolean,previous: Set<Int> = emptySet(),random: Random=Random.Default): Set<Int> {
    require(low in 0..127 && high in low..127 && voices in 1..3 && high-low>=voices-1)
    fun create(): Set<Int> = if(voices==3 && triads && high-low>=7) {
        val root=random.nextInt(low,high-6);setOf(root,root+if(random.nextBoolean())4 else 3,root+7)
    } else (low..high).shuffled(random).take(voices).toSet()
    var next=create();repeat(8){if(next!=previous)return next;next=create()};return next
}
