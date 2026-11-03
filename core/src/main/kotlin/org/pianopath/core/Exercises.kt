package org.pianopath.core

import kotlin.random.Random

enum class ScaleKind(val label: String, val intervals: List<Int>) {
    MAJOR("Major",listOf(0,2,4,5,7,9,11)), NATURAL_MINOR("Natural minor",listOf(0,2,3,5,7,8,10)),
    HARMONIC_MINOR("Harmonic minor",listOf(0,2,3,5,7,8,11)), MELODIC_MINOR("Melodic minor",listOf(0,2,3,5,7,9,11)),
    CHROMATIC("Chromatic",(0..11).toList()), ARPEGGIO("Major arpeggio",listOf(0,4,7))
}
data class Exercise(val score: Score, val xml: String, val guidance: String)
object Exercises {
    private val rhStandard = listOf(1,2,3,1,2,3,4)
    private val lhStandard = listOf(5,4,3,2,1,3,2)
    private fun fingering(kind: ScaleKind, root: Int, hand: Hand, count: Int): List<String> {
        val pattern = when {
            kind == ScaleKind.ARPEGGIO && root == 0 -> if(hand == Hand.RIGHT) listOf(1,2,3) else listOf(5,3,2)
            kind == ScaleKind.MAJOR && root == 11 && hand == Hand.LEFT -> listOf(4,3,2,1,4,3,2)
            kind == ScaleKind.MAJOR && root in listOf(0,2,4,7,9,11) -> if(hand == Hand.RIGHT) rhStandard else lhStandard
            kind == ScaleKind.MAJOR && root == 5 -> if(hand == Hand.RIGHT) listOf(1,2,3,4,1,2,3) else lhStandard
            kind in listOf(ScaleKind.NATURAL_MINOR, ScaleKind.HARMONIC_MINOR,ScaleKind.MELODIC_MINOR) && root in listOf(0,2,7,9) -> if(hand == Hand.RIGHT) rhStandard else lhStandard
            else -> return List(count) { "" } // Never invent fingering for an uncurated key.
        }
        return (0 until count).map { i ->
            if(i == count-1) (if(hand == Hand.RIGHT) if(kind == ScaleKind.MAJOR && root == 5) 4 else 5 else 1).toString()
            else if(hand == Hand.LEFT && kind != ScaleKind.ARPEGGIO && i > 0 && i % 7 == 0) "1"
            else if(kind == ScaleKind.ARPEGGIO && hand == Hand.LEFT && i > 0 && i % 3 == 0) "1"
            else pattern[i % pattern.size].toString()
        }
    }
    fun scale(root: Int = 0, kind: ScaleKind = ScaleKind.MAJOR, octaves: Int = 1, hand: Hand = Hand.BOTH, descending: Boolean = true, bpm: Double = 72.0): Exercise {
        require(root in 0..11 && octaves in 1..2)
        val up = (0 until octaves).flatMap { o -> kind.intervals.map { it + o*12 } } + octaves*12
        val pitches = if(descending) up + up.dropLast(1).reversed() else up
        val notes = mutableListOf<ScoreNote>()
        for(h in listOf(Hand.RIGHT,Hand.LEFT).filter { hand == Hand.BOTH || it == hand }) {
            val fingers = fingering(kind,root,h,up.size)
            val fullFingers = if(descending) fingers + fingers.dropLast(1).reversed() else fingers
            for((i,p) in pitches.withIndex()) {
                val pitch = if(kind == ScaleKind.MELODIC_MINOR && i >= up.size && p % 12 in listOf(9,11)) p - 1 else p
                notes.add(ScoreNote(notes.size, (if(h == Hand.RIGHT) 60 else 48)+root+pitch,i.toDouble(),1.0,i/4+1,if(h == Hand.RIGHT) 1 else 2,finger=fullFingers[i]))
            }
        }
        val measures = (0 until (pitches.size+3)/4).map { Measure(it+1,it*4.0,minOf(4,pitches.size-it*4).toDouble()) }
        val keyFifths = mapOf(0 to 0,1 to -5,2 to 2,3 to -3,4 to 4,5 to -1,6 to 6,7 to 1,8 to -4,9 to 3,10 to -2,11 to 5)[if(kind in listOf(ScaleKind.NATURAL_MINOR,ScaleKind.HARMONIC_MINOR,ScaleKind.MELODIC_MINOR)) (root+3)%12 else root] ?: 0
        val score = Score("${noteName(60+root).dropLast(1)} ${kind.label.lowercase()}","Piano Path · technique",notes,measures,bpm,keyFifths)
        val curated = notes.any { it.finger.isNotBlank() }
        return Exercise(score,write(score),if(curated) "Recommended fingers are shown. A thumb crossing is marked when 1 follows a higher finger. MIDI cannot identify your fingers." else "Fingering has not been curated for this key. MIDI evaluates notes and timing only.")
    }
    fun sight(level: Int = 0, fifths: Int = 0, beats: Int = 4, hand: Hand = Hand.BOTH, seed: Int = Random.nextInt(), bpm: Double = 72.0): Exercise {
        require(level in 0..3 && fifths in -4..4 && beats in listOf(3,4,6))
        val random = Random(seed)
        val tonic = Math.floorMod(fifths*7,12)
        val scale = listOf(0,2,4,5,7,9,11)
        val notes = mutableListOf<ScoreNote>()
        val length = if(beats == 6) 3.0 else beats.toDouble()
        val count = 4 + level*2
        for(h in listOf(Hand.RIGHT,Hand.LEFT).filter { hand == Hand.BOTH || it == hand }) {
            var degree = if(h == Hand.RIGHT) 2 else 0
            for(m in 0 until count) {
                var pos = 0.0
                while(pos < length - 0.001) {
                    val options = when(level) { 0 -> listOf(1.0,2.0); 1 -> listOf(0.5,1.0,2.0); 2 -> listOf(0.5,0.75,1.0,1.5); else -> listOf(0.25,0.5,0.75,1.0,1.5) }
                    val duration = options.filter { it <= length-pos+0.001 }.ifEmpty { listOf(length-pos) }.random(random)
                    val step = listOf(-2,-1,1,2).random(random)
                    degree = (degree+step).coerceIn(if(level==0) 0 else -2,if(level<2) 6 else 10)
                    val pitch = (if(h == Hand.RIGHT) 60 else 48)+tonic+scale[Math.floorMod(degree,7)]+12*Math.floorDiv(degree,7)
                    val rest = level > 0 && random.nextInt(12) == 0
                    notes.add(ScoreNote(notes.size,pitch,m*length+pos,duration,m+1,if(h == Hand.RIGHT) 1 else 2,rest=rest))
                    if(level >= 2 && !rest && random.nextInt(6) == 0) notes.add(ScoreNote(notes.size,(if(h == Hand.RIGHT) 60 else 48)+tonic+scale[Math.floorMod(degree+2,7)]+12*Math.floorDiv(degree+2,7),m*length+pos,duration,m+1,if(h == Hand.RIGHT) 1 else 2))
                    pos += duration
                }
            }
        }
        val score = Score("Sight reading · ${listOf("Beginner","Elementary","Intermediate","Advanced")[level]}","Generated exercise · seed $seed",notes,(0 until count).map { Measure(it+1,it*length,length) },bpm,fifths,beats,if(beats==6) 8 else 4)
        return Exercise(score,write(score),"Read ahead, keep a steady pulse, and continue through mistakes. Exercise seed: $seed.")
    }
    fun write(score: Score): String = buildString {
        fun escape(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><score-partwise version=\"4.0\"><work><work-title>${escape(score.title)}</work-title></work><identification><creator type=\"composer\">${escape(score.composer)}</creator><rights>CC0-1.0 · Piano Path generated exercise</rights></identification><part-list><score-part id=\"P1\"><part-name>Piano</part-name></score-part></part-list><part id=\"P1\">")
        for(m in score.measures) {
            append("<measure number=\"${m.number}\" implicit=\"yes\">")
            if(m.number == 1) append("<attributes><divisions>4</divisions><key><fifths>${score.fifths}</fifths></key><time><beats>${score.beats}</beats><beat-type>${score.beatType}</beat-type></time><staves>2</staves><clef number=\"1\"><sign>G</sign><line>2</line></clef><clef number=\"2\"><sign>F</sign><line>4</line></clef></attributes><direction><direction-type><metronome><beat-unit>quarter</beat-unit><per-minute>${score.tempo.toInt()}</per-minute></metronome></direction-type><sound tempo=\"${score.tempo}\"/></direction>")
            for(staff in 1..2) {
                if(staff == 2) append("<backup><duration>${(m.length*4).toInt()}</duration></backup>")
                val ns = score.notes.filter { it.measure == m.number && it.staff == staff }.sortedBy { it.beat }
                var pos = m.start
                var previous = -1.0
                for(n in ns) {
                    if(n.beat > pos+0.001) append(restXml(n.beat-pos,staff))
                    val chord = n.beat == previous
                    append("<note>")
                    if(chord) append("<chord/>")
                    if(n.rest) append("<rest/>") else {
                        val names = listOf("C","C","D","D","E","F","F","G","G","A","A","B")
                        val p = Math.floorMod(n.midi,12)
                        append("<pitch><step>${names[p]}</step>${if(p in listOf(1,3,6,8,10)) "<alter>1</alter>" else ""}<octave>${n.midi/12-1}</octave></pitch>")
                    }
                    val type = type(n.duration)
                    append("<duration>${(n.duration*4).toInt()}</duration><voice>$staff</voice><type>${type.first}</type>${if(type.second) "<dot/>" else ""}<staff>$staff</staff>")
                    if(n.finger.isNotBlank()) append("<notations><technical><fingering>${n.finger}</fingering></technical></notations>")
                    append("</note>")
                    if(!chord) pos = n.beat+n.duration
                    previous = n.beat
                }
                if(pos < m.start+m.length-0.001) append(restXml(m.start+m.length-pos,staff))
            }
            append("</measure>")
        }
        append("</part></score-partwise>")
    }
    private fun type(d: Double): Pair<String,Boolean> = when(d) { 0.25 -> "16th" to false; 0.5 -> "eighth" to false; 0.75 -> "eighth" to true; 1.0 -> "quarter" to false; 1.5 -> "quarter" to true; 2.0 -> "half" to false; 3.0 -> "half" to true; else -> "whole" to false }
    private fun restXml(d: Double,staff: Int): String { val t = type(d); return "<note><rest/><duration>${(d*4).toInt()}</duration><voice>$staff</voice><type>${t.first}</type>${if(t.second) "<dot/>" else ""}<staff>$staff</staff></note>" }
}
