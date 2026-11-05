package org.pianopath.data

import android.content.Context
import android.util.AtomicFile
import android.util.JsonReader
import android.util.JsonWriter
import org.pianopath.core.MidiRecording
import org.pianopath.core.RecordedMidi
import org.pianopath.core.closeRecording
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/** Private local MIDI files. No score, microphone data, account, or database is involved. */
class RecordingRepository(private val directory: File) {
    constructor(context: Context): this(File(context.filesDir,"recordings"))
    data class Loaded(val recordings: List<MidiRecording>,val messages: List<String>)

    private fun ensureDirectory() { check(directory.isDirectory || directory.mkdirs()) {"Cannot open local recordings folder"} }
    private fun file(id: String,draft: Boolean=false): File {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(directory,"$id${if(draft) ".draft" else ""}.json")
    }

    /** The caller uses one IO writer. The guard also prevents an old checkpoint resurrecting a take. */
    @Synchronized fun checkpoint(value: MidiRecording) {
        ensureDirectory()
        if(file(value.id).exists() || File(file(value.id).path+".bak").exists())return
        write(file(value.id,true),value.copy(finished=false))
    }
    @Synchronized fun save(value: MidiRecording) {
        ensureDirectory();require(value.finished)
        write(file(value.id),value)
        AtomicFile(file(value.id,true)).delete()
    }
    @Synchronized fun delete(id: String) {
        val paths=listOf(file(id),file(id,true))
        paths.forEach {target->
            AtomicFile(target).delete()
            check(!target.exists() && !File(target.path+".bak").exists()) {"Could not delete local recording"}
        }
    }
    @Synchronized fun read(id: String): MidiRecording {
        val final=file(id)
        val value=if(final.exists() || File(final.path+".bak").exists())readFile(final)
        else closeRecording(readFile(file(id,true))).copy(recovered=true)
        require(value.id==id) {"Recording identity does not match its file"}
        return value
    }

    /** Library entries deliberately omit event streams; read(id) opens a complete take. */
    @Synchronized fun load(): Loaded {
        ensureDirectory()
        val messages=mutableListOf<String>()
        // AtomicFile restores a backup if a process died between its rename and commit.
        val names=checkNotNull(directory.listFiles()) {"Cannot list local recordings"}
            .map {it.name.removeSuffix(".bak")}.distinct().filter{it.endsWith(".json")}
        val result=linkedMapOf<String,MidiRecording>()
        fun read(name: String): MidiRecording? {
            val target=File(directory,name)
            return try {
                val value=readFile(target)
                require(file(value.id,name.endsWith(".draft.json")).name==name) {"Recording identity does not match its file"}
                value
            } catch(e: Exception) {
                if(e is IOException && e !is android.util.MalformedJsonException && e !is java.io.EOFException)
                    messages.add("A local recording could not be read; its file was kept.")
                else {
                    val archive=File(directory,"$name.corrupt-${System.currentTimeMillis()}")
                    // Keep malformed bytes for recovery, never silently replace or delete them.
                    if(target.renameTo(archive))messages.add("An unreadable recording was retained as ${archive.name}.")
                    else messages.add("An unreadable recording was kept in the recordings folder.")
                }
                null
            }
        }
        names.filterNot{it.endsWith(".draft.json")}.forEach {name->read(name)?.let {value->
            if(value.finished)result[value.id]=value.copy(events=emptyList()) else messages.add("An unfinished recording was kept for recovery.")
        }}
        names.filter{it.endsWith(".draft.json")}.forEach {name->read(name)?.let {draft->
            if(draft.id in result)AtomicFile(file(draft.id,true)).delete()
            else try {
                val recovered=closeRecording(draft).copy(recovered=true)
                save(recovered);result[recovered.id]=recovered.copy(events=emptyList())
                messages.add("Recovered unfinished recording: ${recovered.title}")
            } catch(e: Exception) {
                // Keep a usable in-memory take and the original draft if storage is full.
                result[draft.id]=closeRecording(draft).copy(recovered=true,events=emptyList())
                messages.add("Recovered a recording, but could not save it. The draft was kept.")
            }
        }}
        return Loaded(result.values.sortedByDescending{it.createdAt},messages)
    }
    private fun write(target: File,value: MidiRecording) {
        value.validate()
        val atomic=AtomicFile(target)
        val output=atomic.startWrite()
        try {
            val writer=JsonWriter(OutputStreamWriter(output,Charsets.UTF_8))
            encode(writer,value);writer.flush()
            atomic.finishWrite(output)
        } catch(e: Exception) {atomic.failWrite(output);throw e}
    }
    private fun encode(out: JsonWriter,value: MidiRecording) {
        out.beginObject().name("version").value(1).name("id").value(value.id).name("title").value(value.title)
            .name("createdAt").value(value.createdAt).name("durationUs").value(value.durationUs)
            .name("finished").value(value.finished).name("recovered").value(value.recovered).name("events").beginArray()
        value.events.forEach{e->out.beginArray().value(e.atUs).value(e.kind).value(e.channel).value(e.a).value(e.b).endArray()}
        out.endArray().endObject()
    }
    private fun readFile(target: File): MidiRecording = AtomicFile(target).openRead().use {input->
        require(input.channel.size()<=MAX_FILE_BYTES) {"Recording file is too large"}
        val reader=JsonReader(InputStreamReader(input,Charsets.UTF_8))
        var version=0;var id:String?=null;var title:String?=null;var date:Long?=null;var duration:Long?=null
        var finished:Boolean?=null;var recovered=false;var events:List<RecordedMidi>?=null
        val fields=mutableSetOf<String>()
        reader.beginObject()
        while(reader.hasNext()) {
            val name=reader.nextName();require(fields.add(name)) {"Duplicate recording field"}
            when(name) {
                "version"->version=reader.nextInt()
                "id"->id=reader.nextString()
                "title"->title=reader.nextString()
                "createdAt"->date=reader.nextLong()
                "durationUs"->duration=reader.nextLong()
                "finished"->finished=reader.nextBoolean()
                "recovered"->recovered=reader.nextBoolean()
                "events"->{
                    val values=ArrayList<RecordedMidi>();reader.beginArray()
                    while(reader.hasNext()) {
                        require(values.size<MidiRecording.MAX_EVENTS*2+8192)
                        reader.beginArray()
                        values.add(RecordedMidi(reader.nextLong(),reader.nextInt(),reader.nextInt(),reader.nextInt(),reader.nextInt()))
                        reader.endArray()
                    }
                    reader.endArray();events=values
                }
                else->reader.skipValue()
            }
        }
        reader.endObject();require(reader.peek()==android.util.JsonToken.END_DOCUMENT)
        require(version==1) {"Unsupported recording version"}
        MidiRecording(requireNotNull(id),requireNotNull(title),requireNotNull(date),requireNotNull(duration),
            requireNotNull(events),requireNotNull(finished),recovered).validate()
    }
    companion object {private const val MAX_FILE_BYTES=32*1024*1024}
}
