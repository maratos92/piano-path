package org.pianopath.audio

import android.content.Context
import android.net.Uri
import org.pianopath.core.PianoSample
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A bounded offline ZIP of pitch-numbered mono PCM16 WAV samples. */
internal class SoundPackStore(private val context: Context) {
    data class Loaded(val samples: List<PianoSample>,val label: String)
    private data class Parsed(val samples: List<PianoSample>,val raw: Map<Int,ByteArray>)
    private val target get()=File(context.filesDir,"custom-piano.zip")
    private val pitchName=Regex("""(?:^|/)([0-9]{1,3})[.]wav$""",RegexOption.IGNORE_CASE)
    private val maxSample=4*1024*1024
    private val maxTotal=48*1024*1024
    fun exists()=target.isFile

    private fun parse(input: InputStream): Parsed {
        val raw=linkedMapOf<Int,ByteArray>()
        var count=0;var total=0
        ZipInputStream(input.buffered()).use { zip ->
            var entry=zip.nextEntry
            while(entry!=null) {
                require(++count<=200){"Too many sound-pack entries"}
                val path=entry.name
                require(path.isNotBlank() && !path.startsWith("/") && !path.contains('\\') &&
                    !path.contains(':') && path.split('/').none{it=="." || it==".."}) {"Unsafe sound-pack path"}
                if(!entry.isDirectory) {
                    val match=pitchName.find(path)
                    if(path.lowercase().endsWith(".wav"))require(match!=null){"Name WAV samples by MIDI note number, e.g. 60.wav"}
                    val maxBytes=if(match!=null)maxSample else 64*1024
                    val bytes=ByteArrayOutputStream()
                    val buffer=ByteArray(8192)
                    while(true) {
                        val n=zip.read(buffer)
                        if(n<0)break
                        total+=n
                        require(total<=maxTotal){"Sound pack is too large (48 MiB extracted limit)"}
                        require(bytes.size()+n<=maxBytes){"Sound-pack entry is too large"}
                        bytes.write(buffer,0,n)
                    }
                    if(match!=null) {
                        val pitch=checkNotNull(match.groupValues[1].toIntOrNull())
                        require(pitch in 0..127 && !raw.containsKey(pitch)){"Invalid or repeated sample pitch"}
                        PianoSample.read(pitch,ByteArrayInputStream(bytes.toByteArray()))
                        raw[pitch]=bytes.toByteArray()
                        require(raw.size<=96){"Too many note samples"}
                    }
                }
                zip.closeEntry()
                entry=zip.nextEntry
            }
        }
        require(raw.isNotEmpty()){"No numbered mono PCM16 WAV samples in ZIP"}
        return Parsed(raw.map{(root,bytes)->PianoSample.read(root,ByteArrayInputStream(bytes))},raw)
    }
    fun load(): Loaded? {
        if(!exists())return null
        val parsed=target.inputStream().use(::parse)
        return Loaded(parsed.samples,"Imported bank · "+parsed.samples.size+" samples")
    }
    fun install(uri: Uri): Loaded {
        val input=context.contentResolver.openInputStream(uri) ?: error("Cannot open selected file")
        val parsed=input.use(::parse)
        val temp=File(context.filesDir,"custom-piano.tmp")
        val old=File(context.filesDir,"custom-piano.bak")
        try {
            ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                parsed.raw.forEach { (root,bytes) ->
                    zip.putNextEntry(ZipEntry(root.toString()+".wav"))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            if(old.exists())old.delete()
            if(target.exists())require(target.renameTo(old)){"Could not back up existing sound pack"}
            if(!temp.renameTo(target)) {
                if(old.exists())old.renameTo(target)
                error("Could not save the imported sound pack")
            }
            old.delete()
        } finally {temp.delete()}
        return Loaded(parsed.samples,"Imported bank · "+parsed.samples.size+" samples")
    }
    fun delete() {
        if(target.exists())require(target.delete()){"Cannot delete imported sound pack"}
        File(context.filesDir,"custom-piano.bak").delete()
    }
}
