package com.luyuan.silkzoom

import org.junit.Assert.*
import org.junit.Test

class StreetFilterTest {
    @Test fun preservesAlphaAndStrengthZeroAndBlendsHalfway() {
        for (filter in StreetFilter.entries) {
            val input=0x71944b24
            assertEquals(input,filter.lut.color(input,0f))
            val full=filter.lut.color(input);val half=filter.lut.color(input,.5f)
            assertEquals(0x71,full ushr 24)
            for(shift in listOf(0,8,16)) assertEquals(((input ushr shift and 255)+(full ushr shift and 255))/2f,(half ushr shift and 255).toFloat(),1f)
        }
    }
    @Test fun monochromeIsNeutralButBlueIsTinted() {
        val gray=StreetFilter.MONO.lut.color(0xffb45a1e.toInt())
        assertEquals(gray ushr 16 and 255,gray ushr 8 and 255);assertEquals(gray ushr 8 and 255,gray and 255)
        val blue=StreetFilter.valueOf("MONO_BLUE").lut.color(0xffb45a1e.toInt())
        assertTrue((blue and 255)>(blue ushr 8 and 255));assertTrue((blue ushr 8 and 255)>(blue ushr 16 and 255))
    }
    @Test fun namedLooksAreDistinctAndBrandAttributionIsExplicit() {
        assertEquals(20,StreetFilter.entries.size)
        assertEquals(20,StreetFilter.entries.map { it.name }.toSet().size)
        val samples=listOf(0xffb45a1e.toInt(),0xff3060b0.toInt(),0xff93bb6b.toInt(),0xff202020.toInt())
        assertEquals(20,StreetFilter.entries.map { f -> samples.map { f.lut.color(it) } }.toSet().size)
        StreetFilter.entries.filter { it.name.startsWith("HASSEL")||it.name.startsWith("LEICA") }.forEach { assertTrue(it.description.contains("自定义"));assertTrue(it.description.contains("不是")) }
    }
}
