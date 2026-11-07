package org.pianopath.audio

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class SoundPackImportTest {
    private fun wav(): ByteArray = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII));putInt(40)
        put("WAVEfmt ".toByteArray(Charsets.US_ASCII));putInt(16)
        putShort(1);putShort(1);putInt(44100);putInt(88200);putShort(2);putShort(16)
        put("data".toByteArray(Charsets.US_ASCII));putInt(4);putShort(0);putShort(500)
    }.array()

    private fun archive(name: String): File {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val target=File.createTempFile("sound-bank-", ".zip",context.cacheDir)
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(wav())
            zip.closeEntry()
        }
        return target
    }

    @Test fun offlineImportKeepsExistingBankWhenLaterArchiveIsUnsafe() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=SoundPackStore(context)
        val good=archive("piano/60.wav")
        val unsafe=archive("../60.wav")
        try {
            val loaded=store.install(Uri.fromFile(good))
            assertEquals(1,loaded.samples.size)
            assertEquals(60,loaded.samples.single().root)
            assertTrue(store.exists())
            assertTrue(runCatching {store.install(Uri.fromFile(unsafe))}.isFailure)
            assertEquals(60,store.load()!!.samples.single().root)
        } finally {
            store.delete();good.delete();unsafe.delete()
        }
    }
}
