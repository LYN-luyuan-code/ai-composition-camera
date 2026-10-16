package com.luyuan.silkzoom

import java.io.Reader
import kotlin.math.*

/** Immutable, bounded SDR RGB lookup table. Red changes fastest, then green, then blue. */
internal class CubeLut private constructor(val size: Int, private val rgb: ByteArray, val title: String) {
    private val lower = IntArray(256) { (it / 255f * (size - 1)).toInt().coerceAtMost(size - 2) }
    private val fraction = FloatArray(256) { it / 255f * (size - 1) - lower[it] }
    fun color(argb: Int, strength: Float = 1f): Int {
        if (strength <= 0f) return argb
        val r = argb ushr 16 and 255; val g = argb ushr 8 and 255; val b = argb and 255
        val ri = lower[r]; val gi = lower[g]; val bi = lower[b]
        val rf = fraction[r]; val gf = fraction[g]; val bf = fraction[b]
        val base = ((bi * size + gi) * size + ri) * 3
        val green = size * 3; val blue = size * size * 3
        fun channel(c: Int, original: Int): Int {
            fun pair(i: Int) = (rgb[i].toInt() and 255) * (1f - rf) + (rgb[i + 3].toInt() and 255) * rf
            val low = pair(base + c) * (1f - gf) + pair(base + green + c) * gf
            val high = pair(base + blue + c) * (1f - gf) + pair(base + blue + green + c) * gf
            return (original + ((low * (1f - bf) + high * bf) - original) * strength.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        }
        return (argb and -0x1000000) or (channel(0,r) shl 16) or (channel(1,g) shl 8) or channel(2,b)
    }
    val tiles: Int get() = ceil(sqrt(size.toDouble())).toInt()
    val atlasSide: Int get() = tiles * size
    fun atlas(): ByteArray {
        val side = atlasSide; val result = ByteArray(side * side * 4)
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
            val source = ((b * size + g) * size + r) * 3
            val dest = (((b / tiles * size + g) * side) + b % tiles * size + r) * 4
            result[dest] = rgb[source]; result[dest+1] = rgb[source+1]; result[dest+2] = rgb[source+2]; result[dest+3] = -1
        }
        return result
    }
    companion object {
        const val MAX_BYTES = 24 * 1024 * 1024
        fun generate(size: Int = 33, title: String = "", transform: (Float,Float,Float) -> FloatArray): CubeLut {
            require(size in 2..65)
            val data = ByteArray(size*size*size*3); var i = 0
            for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
                val color = transform(r.toFloat()/(size-1),g.toFloat()/(size-1),b.toFloat()/(size-1))
                require(color.size == 3 && color.all { it.isFinite() })
                for (c in 0..2) data[i++] = (color[c].coerceIn(0f,1f)*255).roundToInt().toByte()
            }
            return CubeLut(size,data,title)
        }
        fun parse(reader: Reader): CubeLut {
            var title = ""; var n1 = 0; var n3 = 0; var rows = 0; var data: FloatArray? = null
            var minimum = floatArrayOf(0f,0f,0f); var maximum = floatArrayOf(1f,1f,1f)
            var range1: FloatArray? = null; var range3: FloatArray? = null
            val seen = mutableSetOf<String>(); var bytes = 0
            val whitespace = Regex("\\s+")
            reader.buffered().forEachLine { original ->
                bytes += original.length + 1; require(bytes <= MAX_BYTES) { "LUT 文件超过24MB" }
                val line = original.trimStart('\uFEFF').trim()
                if (line.isEmpty() || line.startsWith('#')) return@forEachLine
                if (line.startsWith("TITLE")) {
                    require(rows == 0 && seen.add("TITLE")) { "LUT 标题重复或位置无效" }
                    val m = Regex("^TITLE\\s+\"([^\"]*)\"\\s*(#.*)?$").matchEntire(line)
                    requireNotNull(m) { "LUT 标题格式无效" }; title = m.groupValues[1].take(160); return@forEachLine
                }
                val tokens = line.substringBefore('#').trim().split(whitespace)
                val key = tokens.first()
                fun values(count: Int): FloatArray {
                    require(tokens.size == count + 1) { "LUT 参数数量无效" }
                    return FloatArray(count) { tokens[it+1].toFloatOrNull()?.takeIf(Float::isFinite) ?: error("LUT 含无效数值") }
                }
                when (key) {
                    "LUT_1D_SIZE", "LUT_3D_SIZE", "DOMAIN_MIN", "DOMAIN_MAX", "LUT_1D_INPUT_RANGE", "LUT_3D_INPUT_RANGE" -> {
                        require(rows == 0 && seen.add(key)) { "LUT 参数重复或出现在数据之后" }
                        when (key) {
                            "LUT_1D_SIZE" -> { require(tokens.size == 2); n1 = tokens[1].toIntOrNull() ?: 0; require(n1 in 2..65536) { "1D LUT 支持2–65536点" } }
                            "LUT_3D_SIZE" -> { require(tokens.size == 2); n3 = tokens[1].toIntOrNull() ?: 0; require(n3 in 2..65) { "3D LUT 支持2–65点" } }
                            "DOMAIN_MIN" -> minimum = values(3)
                            "DOMAIN_MAX" -> maximum = values(3)
                            "LUT_1D_INPUT_RANGE" -> range1 = values(2)
                            "LUT_3D_INPUT_RANGE" -> range3 = values(2)
                        }
                    }
                    else -> {
                        require(n1 > 0 || n3 > 0) { "缺少 LUT_1D_SIZE 或 LUT_3D_SIZE" }
                        require(tokens.size == 3) { "LUT 数据每行需要3个数值" }
                        val expected = n1 + n3*n3*n3
                        if (data == null) data = FloatArray(expected*3)
                        require(rows < expected) { "LUT 数据行数过多" }
                        for (c in 0..2) data!![rows*3+c] = tokens[c].toFloatOrNull()?.takeIf(Float::isFinite) ?: error("LUT 含未知参数或非有限数值")
                        rows++
                    }
                }
            }
            require((n1 > 0 || n3 > 0) && rows == n1 + n3*n3*n3) { "LUT 数据不完整" }
            require((0..2).all { minimum[it] < maximum[it] }) { "LUT 输入范围无效" }
            for (range in listOfNotNull(range1,range3)) require(range[0] < range[1]) { "LUT 输入范围无效" }
            val table = data!!
            if (n1==0 && n3>0 && range3==null && minimum.contentEquals(floatArrayOf(0f,0f,0f)) && maximum.contentEquals(floatArrayOf(1f,1f,1f)) && table.all { it in 0f..1f }) {
                return CubeLut(n3,ByteArray(table.size){(table[it]*255).roundToInt().toByte()},title)
            }
            fun normalized(x: Float,c: Int,range: FloatArray?, useDomain: Boolean) =
                ((x - (range?.get(0) ?: if (useDomain) minimum[c] else 0f)) /
                    ((range?.get(1) ?: if (useDomain) maximum[c] else 1f) - (range?.get(0) ?: if (useDomain) minimum[c] else 0f))).coerceIn(0f,1f)
            // Bake domains and an optional 1D shaper into a bounded display-referred 65³ table.
            return generate(65,title) { r,g,b ->
                val input = floatArrayOf(r,g,b)
                if (n1 > 0) for (c in 0..2) {
                    val v = normalized(input[c],c,range1,true)*(n1-1); val low = v.toInt().coerceAtMost(n1-2); val f = v-low
                    input[c] = table[low*3+c]*(1-f)+table[(low+1)*3+c]*f
                }
                if (n3 == 0) input else {
                    val p = FloatArray(3) { normalized(input[it],it,range3,n1==0)*(n3-1) }
                    val lower = IntArray(3) { p[it].toInt().coerceAtMost(n3-2) }; val f = FloatArray(3) { p[it]-lower[it] }
                    FloatArray(3) { c ->
                        var value = 0f
                        for (z in 0..1) for (y in 0..1) for (x in 0..1) {
                            val i = n1*3+(((lower[2]+z)*n3+lower[1]+y)*n3+lower[0]+x)*3+c
                            value += table[i]*(if(x==0)1-f[0] else f[0])*(if(y==0)1-f[1] else f[1])*(if(z==0)1-f[2] else f[2])
                        };value
                    }
                }
            }
        }
    }
}
