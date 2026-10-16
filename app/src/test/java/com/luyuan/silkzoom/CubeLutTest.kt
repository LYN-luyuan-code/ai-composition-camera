package com.luyuan.silkzoom

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

class CubeLutTest {
    private fun cube(header:String="LUT_3D_SIZE 2", swap:Boolean=false)=header+"\n"+buildString {
        for(b in 0..1)for(g in 0..1)for(r in 0..1)append(if(swap)"$b $g $r\n" else "$r $g $b\n")
    }
    private fun parse(s:String)=CubeLut.parse(s.reader())
    @Test fun redFastestOrderTrilinearInterpolationAndIdentity() {
        val swap=parse(cube(swap=true));val identity=parse(cube())
        for(i in 0..300) {
            val c=Random(i).nextInt()
            assertEquals(c,identity.color(c))
            assertEquals((c and -0x1000000) or ((c and 255) shl 16) or (c and 0x0000ff00) or (c ushr 16 and 255),swap.color(c))
        }
    }
    @Test fun supportsBomCommentsScientificNumbersAndQuotedTitle() {
        val lut=parse(cube("\uFEFF# comment\r\nTITLE \"Blue #1\" # comment\r\nLUT_3D_SIZE 2").replace("1 1 1","1e0 1.0 1.000 # white"))
        assertEquals("Blue #1",lut.title);assertEquals(0xff80a020.toInt(),lut.color(0xff80a020.toInt()))
    }
    @Test fun domainsAreAppliedPerInputChannelAndClampOutside() {
        val lut=parse(cube("LUT_3D_SIZE 2\nDOMAIN_MIN 0.2 0.0 0.0\nDOMAIN_MAX 0.8 1.0 1.0"))
        assertEquals(0, lut.color(0xff003040.toInt()) ushr 16 and 255)
        assertEquals(255, lut.color(0xffff3040.toInt()) ushr 16 and 255)
        assertEquals(128f,(lut.color(0xff803040.toInt()) ushr 16 and 255).toFloat(),2f)
    }
    @Test fun supportsOneDimensionalAndCombinedShaperTables() {
        val shaper="LUT_1D_SIZE 2\n0 0 0\n0.5 1 1\n"
        assertEquals(128f,(parse(shaper).color(0xffff0000.toInt()) ushr 16 and 255).toFloat(),1f)
        val combined=cube("LUT_1D_SIZE 2\nLUT_3D_SIZE 2\n0 0 0\n0.5 1 1",swap=true)
        assertEquals(0xff000080.toInt(),parse(combined).color(0xffff0000.toInt()))
        val range=parse(cube("LUT_3D_SIZE 2\nLUT_3D_INPUT_RANGE 0 0.5"))
        assertEquals(255,range.color(0xff808080.toInt()) and 255)
    }
    @Test fun rejectsBrokenOrUnboundedFilesWithoutPartialTable() {
        for(s in listOf("",cube().substringBeforeLast("1 1 1"),cube()+"0 0 0\n",cube().replace("LUT_3D_SIZE 2","LUT_3D_SIZE 66"),cube().replace("0 0 0","NaN 0 0"),cube().replace("0 0 0","Infinity 0 0"),cube("LUT_3D_SIZE 2\nDOMAIN_MIN 1 1 1"),cube("LUT_3D_SIZE 2\nLUT_3D_SIZE 2"),cube("LUT_3D_SIZE 2\nUNKNOWN 1"))) {
            assertThrows(Exception::class.java){ parse(s) }
        }
        assertThrows(Exception::class.java){parse("#"+"x".repeat(CubeLut.MAX_BYTES))}
    }
    @Test fun atlasSlicesMatchCpuCornersWithNoChannelTranspose() {
        for(n in listOf(2,17,33,65)) {
            val lut=CubeLut.generate(n) { r,g,b -> floatArrayOf(b,g,r) };val data=lut.atlas()
            for(b in 0 until n)for(g in 0 until n)for(r in 0 until n) {
                val pos=((b/lut.tiles*n+g)*lut.atlasSide+b%lut.tiles*n+r)*4
                assertEquals((b*255f/(n-1)).toInt().toFloat(),(data[pos].toInt() and 255).toFloat(),1f)
                assertEquals((g*255f/(n-1)).toInt().toFloat(),(data[pos+1].toInt() and 255).toFloat(),1f)
                assertEquals((r*255f/(n-1)).toInt().toFloat(),(data[pos+2].toInt() and 255).toFloat(),1f)
                assertEquals(255,data[pos+3].toInt() and 255)
            }
        }
    }
    @Test fun bundledLicensedCinemaFilesAreValidAndDistinct() {
        val files=listOf("60","80").map { File("src/main/assets/luts/cinema$it.cube") }
        val colors=files.map { it.reader().use(CubeLut::parse).color(0xff907050.toInt()) }
        assertNotEquals(colors[0],colors[1]);assertTrue(File("src/main/assets/luts/LICENSE.txt").readText().contains("MIT"))
    }
}
