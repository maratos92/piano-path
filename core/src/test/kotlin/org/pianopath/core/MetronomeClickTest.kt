package org.pianopath.core

import org.junit.Assert.*
import org.junit.Test

class MetronomeClickTest {
    @Test fun mutedClickLeavesExistingAudioUnchangedAndStillFinishes() {
        val click=MetronomeClick();click.volume=0f;click.trigger(true)
        val buffer=FloatArray(5000){.125f};click.mix(buffer)
        assertTrue(buffer.all{it==.125f});assertFalse(click.active)
    }
    @Test fun audibleClickIsFiniteBoundedAndDecaysToSilence() {
        val click=MetronomeClick();click.volume=1f;click.trigger(false)
        val buffer=FloatArray(5000);click.mix(buffer)
        assertTrue(buffer.all{it.isFinite() && it in -1f..1f})
        assertTrue(buffer.any{it!=0f})
        assertTrue(buffer.takeLast(500).all{it==0f});assertFalse(click.active)
    }
}
