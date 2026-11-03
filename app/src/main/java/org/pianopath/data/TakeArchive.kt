package org.pianopath.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.pianopath.PracticeUi
import org.pianopath.core.*
import java.io.File
import java.util.UUID
import kotlin.math.*

/** Private, offline takes include their own score so importing another edition cannot alter a review. */
object TakeArchive {
    fun save(context: Context,p: PracticeUi,r: SessionResult): String {
        val data=JSONObject().put("xml",p.xml).put("bpm",p.bpm).put("startMeasure",p.startMeasure).put("endMeasure",p.endMeasure)
            .put("startOffset",p.startOffset).put("endOffset",p.endOffset).put("tolerance",p.visualTolerance)
            .put("mode",p.mode.name).put("hand",p.hand.name)
            .put("take",JSONArray(r.take.map {n->JSONObject().put("id",n.id).put("pitch",n.pitch).put("velocity",n.velocity).put("channel",n.channel)
                .put("beat",n.beat).put("duration",n.duration ?: JSONObject.NULL).put("expectedId",n.expectedId ?: JSONObject.NULL).put("onsetMs",n.onsetMs ?: JSONObject.NULL)}))
            .put("details",JSONArray(r.details.map{n->JSONObject().put("id",n.note.id).put("onset",n.onsetMs ?: JSONObject.NULL)
                .put("duration",n.durationErrorMs ?: JSONObject.NULL).put("missing",n.missing).put("channel",n.channel)}))
        val directory=File(context.filesDir,"takes").apply{mkdirs()}
        val name="${UUID.randomUUID()}.json"
        val temporary=File(directory,"$name.tmp");temporary.writeText(data.toString())
        check(temporary.renameTo(File(directory,name))){"Could not store visual review"}
        return name
    }
    fun load(context: Context,session: SessionEntity): PracticeUi {
        val name=requireNotNull(session.reviewFile);require(name.matches(Regex("[a-f0-9-]+\\.json")))
        val data=JSONObject(File(context.filesDir,"takes/$name").readText());val xml=data.getString("xml");val score=MusicXml.parse(xml)
        fun nullable(o: JSONObject,key: String)=if(o.isNull(key))null else o.getDouble(key)
        val take=data.getJSONArray("take").let {a->(0 until a.length()).map {i->val n=a.getJSONObject(i)
            PlayedNote(n.getInt("id"),n.getInt("pitch"),n.getInt("velocity"),n.getInt("channel"),n.getDouble("beat"),nullable(n,"duration"),if(n.isNull("expectedId"))null else n.getInt("expectedId"),nullable(n,"onsetMs"))}}
        val notes=score.notes.associateBy{it.id}
        val details=data.getJSONArray("details").let{a->(0 until a.length()).mapNotNull {i->val n=a.getJSONObject(i);notes[n.getInt("id")]?.let{
            NoteResult(it,nullable(n,"onset"),nullable(n,"duration"),n.getBoolean("missing"),n.getInt("channel"))}}}
        val chords=details.groupBy{it.note.beat}.values.filter{it.size>1}
        val onsets=details.mapNotNull{it.onsetMs}
        fun handPitch(hand: Hand): Double? {val notes=details.filter{it.note.hand==hand};return if(notes.isEmpty())null else notes.count{it.onsetMs!=null}*100.0/notes.size}
        val consistency=if(onsets.size<2)null else sqrt(onsets.sumOf{(it-onsets.average()).pow(2)}/onsets.size)
        val r=SessionResult(session.expected,session.correct,session.missing,session.extra,session.pitch,session.rhythm,session.overall,session.onsetMs,session.durationMs,
            details.count{(it.onsetMs ?: 0.0)<-data.getDouble("tolerance")},details.count{(it.onsetMs ?: 0.0)>data.getDouble("tolerance")},chords.count{chord->chord.all{it.onsetMs!=null}},chords.size,handPitch(Hand.LEFT),handPitch(Hand.RIGHT),consistency,details,take)
        return PracticeUi(score=score,xml=xml,key="review-${session.id}",category=session.category,bpm=data.getDouble("bpm"),mode=PracticeMode.valueOf(data.getString("mode")),hand=Hand.valueOf(data.getString("hand")),
            startMeasure=data.getInt("startMeasure"),endMeasure=data.getInt("endMeasure"),startOffset=data.getDouble("startOffset"),endOffset=data.getDouble("endOffset"),
            take=take,matched=details.filter{it.onsetMs!=null}.map{it.note.id}.toSet(),missed=details.filter{it.missing}.map{it.note.id}.toSet(),
            review=true,reviewBeat=score.measures[data.getInt("startMeasure")-1].start+data.getDouble("startOffset"),result=r,view=org.pianopath.ScoreView.SHEET,
            visualTolerance=data.getDouble("tolerance"),simulated=session.simulated,feedback="Recorded performance · ${session.correct}/${session.expected} correct · ${session.extra} extra")
    }
}
