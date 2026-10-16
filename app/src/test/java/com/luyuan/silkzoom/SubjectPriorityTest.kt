package com.luyuan.silkzoom

import org.junit.Assert.*
import org.junit.Test

class SubjectPriorityTest {
    @Test fun expressiveRelationshipsCanOutrankAnObviousButOrdinaryObject() {
        assertTrue(SubjectPriority.score(.9f, .8f, .85f, .6f) > SubjectPriority.score(.2f, .1f, .3f, 1f))
        assertThrows(IllegalArgumentException::class.java) { SubjectPriority.score(Float.NaN, 1f, 1f, 1f) }
    }
    @Test fun staticSceneCheckToleratesExposureButRejectsChangedStructure() {
        val a = IntArray(320) { if (it % 7 < 3) 0x303030 else 0xA0A0A0 }
        val brighter = IntArray(320) { if (it % 7 < 3) 0x404040 else 0xB0B0B0 }
        val different = IntArray(320) { if (it % 7 < 3) 0xA0A0A0 else 0x303030 }
        assertTrue(SubjectPriority.sameScene(a, brighter))
        assertFalse(SubjectPriority.sameScene(a, different))
    }
}
