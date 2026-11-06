package org.pianopath.core

import org.junit.Assert.*
import org.junit.Test

class KeyboardGeometryTest {
    @Test fun fullPianoMatchesExistingLayoutAndConfiguredRangesExcludeOtherKeys() {
        val full=KeyboardGeometry()
        assertEquals(PianoLayout.keys,full.keys)
        assertEquals(52.0,full.whiteSpan,.001)
        val narrow=KeyboardGeometry(48..72)
        assertEquals(48,narrow.keys.first().pitch);assertEquals(72,narrow.keys.last().pitch)
        assertNull(narrow.key(47));assertNull(narrow.key(73))
        assertTrue(narrow.keys.all{it.pitch in 48..72})
    }
    @Test fun blackEndpointsAndSingletonArePlayableWithoutInventingWhiteKeys() {
        val geometry=KeyboardGeometry(37..39) // C-sharp, D, D-sharp
        assertEquals(setOf(37,38,39),geometry.keys.map{it.pitch}.toSet())
        assertEquals(1.0,geometry.whiteSpan,.001)
        assertEquals(37,geometry.hit(.1,.2));assertEquals(39,geometry.hit(.9,.2))
        assertEquals(38,geometry.hit(.1,.9));assertEquals(38,geometry.hit(.9,.9))
        val single=KeyboardGeometry(37..37)
        assertEquals(37,single.hit(single.whiteSpan/2,.2))
        assertNull(single.hit(single.whiteSpan/2,.9))
    }
    @Test fun coordinateMappingKeepsEveryKeyAlignedAtZoomAndPan() {
        val geometry=KeyboardGeometry(48..84)
        for(zoom in listOf(1.0,2.5,6.0))for(pan in listOf(0.0,.5,1.0))for(key in geometry.keys) {
            val unit=key.left+key.width/2
            assertEquals(unit,geometry.unitX(geometry.screenX(unit,zoom,pan),zoom,pan),.000001)
        }
    }
    @Test fun invalidRangesAreRejected() {
        assertThrows(IllegalArgumentException::class.java){KeyboardGeometry(-1..10)}
        assertThrows(IllegalArgumentException::class.java){KeyboardGeometry(60..59)}
        assertThrows(IllegalArgumentException::class.java){KeyboardGeometry(100..128)}
    }
}
