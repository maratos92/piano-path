package org.pianopath.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ImportLimitsTest {
    private fun archive(directoryCount: Int): ByteArray {
        val output=ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            repeat(directoryCount) { zip.putNextEntry(ZipEntry("directory-$it/"));zip.closeEntry() }
            zip.putNextEntry(ZipEntry("score.musicxml"));zip.write("<score-partwise/>".toByteArray());zip.closeEntry()
        }
        return output.toByteArray()
    }

    @Test fun archiveEntryLimitCountsDirectoriesAsWellAsFiles() {
        assertEquals("<score-partwise/>",MusicXml.unpack(archive(255)))
        assertThrows(IllegalArgumentException::class.java){MusicXml.unpack(archive(256))}
    }
    @Test fun nonfiniteDurationCannotCreateAnUnplayableScore() {
        val xml="""<score-partwise><part id="P1"><measure number="1">
            <attributes><divisions>1</divisions></attributes>
            <note><pitch><step>C</step><octave>4</octave></pitch><duration>NaN</duration></note>
            </measure></part></score-partwise>"""
        assertThrows(IllegalArgumentException::class.java){MusicXml.parse(xml)}
    }
}
