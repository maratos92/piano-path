package org.pianopath.core

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max

object MusicXml {
    const val MAX_BYTES = 12 * 1024 * 1024
    private fun Element.children(name: String? = null): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { name == null || it.tagName == name }
    private fun Element.one(name: String) = children(name).firstOrNull()
    private fun Element.txt(name: String, default: String = "") = one(name)?.textContent?.trim() ?: default
    private fun number(text: String,label: String,min: Double=0.0,max: Double=1_000_000_000.0): Double {
        val value=text.toDoubleOrNull()
        require(value!=null && value.isFinite() && value in min..max) {"Invalid $label"}
        return value
    }
    private fun integer(text: String,label: String,min: Int,max: Int): Int {
        val value=text.toIntOrNull();require(value!=null && value in min..max){"Invalid $label"};return value
    }
    private fun safePath(name: String): Boolean = name.isNotBlank() && !name.startsWith("/") && '\\' !in name && ':' !in name && '\u0000' !in name && name.split('/').none{it==".." || it=="."}
    private fun document(bytes: ByteArray): org.w3c.dom.Document {
        require(!Regex("<!\\s*ENTITY\\b").containsMatchIn(bytes.toString(Charsets.UTF_8))) { "Entity declarations are not supported" }
        return DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isExpandEntityReferences = false
        // MuseScore files may have a DOCTYPE. Never fetch external declarations or entities.
        try { setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) { }
        try { setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) { }
        try { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) } catch (_: Exception) { }
    }.newDocumentBuilder().apply { setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) } }.parse(ByteArrayInputStream(bytes))
    }
    fun unpack(bytes: ByteArray): String {
        require(bytes.size <= MAX_BYTES) { "Score exceeds 12 MB import limit" }
        if(bytes.size < 2 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4b.toByte()) return bytes.toString(Charsets.UTF_8)
        val files = linkedMapOf<String, ByteArray>()
        var total = 0
        val names=mutableSetOf<String>();var entryCount=0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while(entry != null) {
                require(++entryCount <= 256) { "Too many archive entries" }
                val name = entry.name
                require(safePath(name)) { "Unsafe archive path" }
                require(names.add(name)) {"Duplicate archive path"}
                if(entry.isDirectory) { require(zip.read()==-1){"Archive directory contains data"} } else {
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var read = zip.read(buffer)
                    while(read > 0) { total += read; require(total <= MAX_BYTES) { "Expanded score exceeds 12 MB" }; out.write(buffer,0,read); read = zip.read(buffer) }
                    files[name] = out.toByteArray()
                }
                entry = zip.nextEntry
            }
        }
        val container = files["META-INF/container.xml"]
        val root = container?.let { document(it).getElementsByTagName("rootfile").item(0) as? Element }?.getAttribute("full-path")
        if(root!=null)require(safePath(root) && files.containsKey(root)){"Invalid MusicXML rootfile"}
        val xml = files[root] ?: files.entries.firstOrNull { !it.key.startsWith("META-INF/") && (it.key.endsWith(".xml") || it.key.endsWith(".musicxml")) }?.value
        return requireNotNull(xml) { "No MusicXML document in archive" }.toString(Charsets.UTF_8)
    }
    fun parse(xml: String): Score {
        require(xml.toByteArray().size <= MAX_BYTES)
        val root = document(xml.toByteArray()).documentElement
        require(root.tagName == "score-partwise") { "Import a partwise MusicXML file (MuseScore default)" }
        val title = root.one("work")?.txt("work-title")?.ifBlank { null } ?: root.txt("movement-title", "Untitled score")
        val composer = root.one("identification")?.children("creator")?.firstOrNull { it.getAttribute("type") == "composer" }?.textContent ?: "Unknown composer"
        val all = mutableListOf<ScoreNote>()
        val graceNotes = mutableListOf<GraceNote>()
        val ornaments = mutableListOf<OrnamentMark>()
        val measures = mutableListOf<Measure>()
        var tempo = 80.0; var fifths = 0; var initialBeats = 4; var initialType = 4
        val tempoMarks=mutableListOf<TempoMark>()
        var expressiveTempo=false
        val part = root.children("part").firstOrNull() ?: error("No musical part found")
        require(root.children("part").size == 1) { "Import a single piano part with grand staff; multi-instrument scores are not supported" }
        var divisions = 1.0; var start = 0.0; var beats = 4; var beatType = 4; var transpose = 0
        val ties = mutableMapOf<Triple<Int,Int,String>,Int>()
        val sourceMeasures=part.children("measure")
        require(sourceMeasures.size in 1..20_000){"Unsupported measure count"}
        for((index,m) in sourceMeasures.withIndex()) {
            var pos = 0.0; var extent = 0.0; var chordStart = 0.0
            for(e in m.children()) when(e.tagName) {
                "attributes" -> {
                    divisions = number(e.txt("divisions",divisions.toString()),"divisions",1.0)
                    e.one("time")?.let { t -> val count=t.txt("beats","4").split('+').sumOf{integer(it,"time beats",1,1024).toLong()};require(count in 1..1024){"Invalid time beats"};beats=count.toInt();beatType=integer(t.txt("beat-type","4"),"beat type",1,1024) }
                    e.one("transpose")?.let { transpose = integer(it.txt("chromatic","0"),"chromatic transpose",-127,127) + 12 * integer(it.txt("octave-change","0"),"octave transpose",-10,10) }
                    if(index == 0) { fifths = e.one("key")?.txt("fifths","0")?.let{integer(it,"key signature",-21,21)} ?: 0; initialBeats = beats; initialType = beatType }
                }
                "direction" -> {
                    val sound = e.one("sound")?.getAttribute("tempo")?.toDoubleOrNull()
                    val metro = e.one("direction-type")?.one("metronome")
                    val unit = mapOf("whole" to 4.0,"half" to 2.0,"quarter" to 1.0,"eighth" to 0.5)[metro?.txt("beat-unit")] ?: 1.0
                    val bpm = sound ?: metro?.txt("per-minute")?.toDoubleOrNull()?.let { it * unit * if(metro.one("beat-unit-dot") != null) 1.5 else 1.0 }
                    if(bpm != null && bpm in 10.0..400.0) {
                        if(index==0 && pos<.0001)tempo=bpm
                        tempoMarks.add(TempoMark(start+pos,bpm))
                    }
                    if(Regex("(?i)\\b(accel|accelerando|rit|ritard|ritardando|rall|rallentando|rubato)\\b")
                        .containsMatchIn(e.one("direction-type")?.txt("words") ?: "")) expressiveTempo=true
                }
                "backup" -> {val amount=number(e.txt("duration","0"),"backup duration")/divisions;require(amount<=pos+.0001){"Backup precedes measure"};pos=(pos-amount).coerceAtLeast(0.0)}
                "forward" -> { pos += number(e.txt("duration","0"),"forward duration") / divisions;require(pos<=1_000_000){"Measure is too long"}; extent = max(extent,pos) }
                "note" -> {
                    val grace=e.one("grace")
                    if(grace!=null) {
                        // Grace notes have no <duration> in MusicXML. Keep their written
                        // pitch/order and voice separately from assessed score notes.
                        val pitch=e.one("pitch")
                        if(pitch!=null) {
                            val step=requireNotNull(mapOf("C" to 0,"D" to 2,"E" to 4,"F" to 5,"G" to 7,"A" to 9,"B" to 11)[pitch.txt("step")])
                            val alteration=number(pitch.txt("alter","0"),"grace accidental",-12.0,12.0)
                            require(alteration==alteration.toInt().toDouble()){"Microtonal grace notes unsupported"}
                            val midi=(integer(pitch.txt("octave","4"),"grace octave",-1,10)+1)*12+step+alteration.toInt()+transpose
                            require(midi in 0..127){"Grace note outside MIDI range"}
                            graceNotes.add(GraceNote(midi,start+pos,index+1,
                                integer(e.txt("staff","1"),"grace staff",1,16),e.txt("voice","1"),grace.getAttribute("slash")=="yes"))
                        }
                        continue
                    }
                    val duration = number(e.txt("duration","0"),"note duration")/divisions
                    require(duration in 0.0..1_000_000.0){"Note is too long"}
                    val chord = e.one("chord") != null
                    if(!chord) chordStart = pos
                    val beat = start + if(chord) chordStart else pos
                    val pitch = e.one("pitch")
                    val rest = e.one("rest") != null
                    require(rest || pitch!=null){"Unpitched notes are not supported"}
                    val midi = if(pitch == null) 0 else {
                        val step=requireNotNull(mapOf("C" to 0,"D" to 2,"E" to 4,"F" to 5,"G" to 7,"A" to 9,"B" to 11)[pitch.txt("step")]){"Invalid pitch step"}
                        val alter=number(pitch.txt("alter","0"),"pitch alteration",-12.0,12.0)
                        require(alter==alter.toInt().toDouble()){"Microtonal pitches are not supported"}
                        (integer(pitch.txt("octave","4"),"pitch octave",-1,10)+1)*12+step+alter.toInt()+transpose
                    }
                    require(rest || midi in 0..127){"Pitch is outside MIDI range"}
                    val staff = integer(e.txt("staff","1"),"staff",1,16)
                    val voice = e.txt("voice","1")
                    val key = Triple(midi,staff,voice)
                    val types = (e.children("tie") + (e.one("notations")?.children("tied") ?: emptyList())).map { it.getAttribute("type") }
                    val previous = if("stop" in types) ties[key] else null
                    val id: Int
                    if(previous != null) { id = previous; all[id] = all[id].copy(duration = max(all[id].duration,beat + duration - all[id].beat)) }
                    else { require(all.size<100_000){"Score contains too many notes"};id = all.size; all.add(ScoreNote(id,midi,beat,duration,index+1,staff,voice,e.one("notations")?.one("technical")?.txt("fingering") ?: "",rest)) }
                    if(!rest) e.one("notations")?.one("ornaments")?.children()?.forEach { mark ->
                        val kind=when(mark.tagName) {
                            "trill-mark" -> OrnamentKind.TRILL
                            "inverted-mordent" -> OrnamentKind.INVERTED_MORDENT
                            else -> null
                        }
                        if(kind!=null)ornaments.add(OrnamentMark(id,kind))
                    }
                    if("start" in types) ties[key] = id else ties.remove(key)
                    if(!chord) pos += duration
                    extent = max(extent, if(chord) chordStart + duration else pos)
                }
            }
            val nominal = beats * 4.0 / beatType
            val length = if(m.getAttribute("implicit") == "yes" || index == 0 && extent < nominal) extent.coerceAtLeast(0.25) else max(nominal,extent)
            measures.add(Measure(index+1,start,length,m.getAttribute("number").ifBlank { (index+1).toString() }))
            start += length
            require(start.isFinite() && start<=1_000_000){"Score is too long"}
        }
        require(all.all{it.beat.isFinite() && it.beat>=0 && it.duration.isFinite() && it.duration>=0 && (it.beat+it.duration)<=start+.001}){"Invalid score timing"}
        require(all.any { !it.rest }) { "The score has no pitched notes" }
        require(all.size <= 100_000) { "Score contains too many notes" }
        return Score(title,composer,all,measures,tempo.coerceIn(20.0,300.0),fifths,initialBeats,initialType,tempoMarks.sortedBy {it.beat},expressiveTempo,graceNotes,ornaments.distinct())
    }
}
