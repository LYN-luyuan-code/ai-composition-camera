package com.luyuan.silkzoom

import kotlin.math.*

class StreetFilter private constructor(val name: String, val label: String, val group: String,
    val description: String, val imported: Boolean = false, loader: () -> CubeLut) {
    internal val lut: CubeLut by lazy(loader)
    companion object {
        private data class Grade(val saturation: Float=1f, val contrast: Float=1f, val gamma: Float=1f,
            val lift: Float=0f, val white: Float=1f, val warm: Float=0f, val teal: Float=0f,
            val magenta: Float=0f, val brown: Float=0f, val monochrome: Int=0)
        private fun preset(id: String,label: String,group: String="色彩", description: String="本应用自定义调色", grade: Grade=Grade()) =
            StreetFilter(id,label,group,description) { CubeLut.generate(title=label) { r,g,b ->
                val y=.2126f*r+.7152f*g+.0722f*b
                if(grade.monochrome > 0) {
                    val v=(grade.lift+(grade.white-grade.lift)*((y-.5f)*grade.contrast+.5f).coerceIn(0f,1f).pow(grade.gamma))
                    if(grade.monochrome==2) floatArrayOf(.015f+v*.38f,.025f+v*.68f,.06f+v*.94f) else floatArrayOf(v,v,v)
                } else {
                    val values=floatArrayOf(r,g,b)
                    for(c in 0..2) values[c]=grade.lift+(grade.white-grade.lift)*((y+(values[c]-y)*grade.saturation-.5f)*grade.contrast+.5f).coerceIn(0f,1f).pow(grade.gamma)
                    val shadow=(1-y).pow(2);val high=y*y;val mid=4*y*(1-y)
                    values[0]+=grade.warm*.065f*high-grade.teal*.035f*shadow+grade.magenta*.025f*mid+grade.brown*.04f*mid
                    values[1]+=grade.teal*.032f*shadow-grade.magenta*.02f*mid+grade.brown*.008f*mid
                    values[2]-=grade.warm*.06f*high-grade.teal*.045f*shadow+grade.brown*.055f*mid
                    values
                }
            } }
        val ORIGINAL=StreetFilter("ORIGINAL","原片","基础","不改变色彩") { CubeLut.generate(2) { r,g,b -> floatArrayOf(r,g,b) } }
        val SOFT=preset("SOFT","柔和",grade=Grade(.86f,.92f,.96f,.025f,.985f))
        val WARM=preset("WARM","暖调",grade=Grade(1.03f,1f,.98f,warm=.8f))
        val COOL=preset("COOL","冷调",grade=Grade(.98f,1.02f,teal=.7f,warm=-.5f))
        val MONO=preset("MONO","黑白","单色",grade=Grade(contrast=1.04f,monochrome=1))
        private const val HASSEL="参考哈苏自然色彩与平滑影调的自定义风格，不是哈苏原版 HNCS 或官方 LUT。"
        private const val LEICA="参考徕卡官方 Looks 风格描述的自定义调色，不是徕卡官方原版 LUT。"
        val entries: List<StreetFilter> = listOf(ORIGINAL,SOFT,WARM,COOL,MONO,
            preset("HASSEL_NAT","哈苏自然",description=HASSEL,grade=Grade(.94f,.98f,.98f,.008f,.99f,.15f)),
            preset("HASSEL_FILM","哈苏电影","胶片",HASSEL,Grade(.87f,1.08f,.96f,.022f,.975f,.35f,.15f)),
            preset("LEICA_NAT","徕卡自然",description=LEICA,grade=Grade(.84f,1.02f,1.02f,.008f,.99f,.12f)),
            preset("LEICA_VIVID","徕卡鲜艳",description=LEICA,grade=Grade(1.2f,1.14f,1.01f)),
            preset("LEICA_CLASSIC","徕卡经典","胶片",LEICA,Grade(.8f,1.12f,.95f,.025f,.97f,.5f)),
            preset("LEICA_MONO","徕卡高反","单色",LEICA,Grade(contrast=1.28f,gamma=1.04f,monochrome=1)),
            preset("CINEMA","电影青橙","胶片",grade=Grade(.88f,1.12f,.98f,.02f,.98f,.9f,.95f)),
            preset("BLACK_GOLD","黑金",grade=Grade(.65f,1.28f,1.08f,warm=1.6f,teal=.3f)),
            preset("BLOSSOM","繁花如梦",grade=Grade(1.22f,.95f,.93f,.035f,.99f,.2f,magenta=.7f)),
            preset("POSITIVE","正片","胶片",grade=Grade(1.18f,1.16f,1.03f,white=.99f,warm=.12f)),
            preset("NEGATIVE","负片","胶片",grade=Grade(.82f,.9f,.94f,.045f,.97f,.35f,.35f)),
            preset("FADE_BROWN","飘棕","胶片",grade=Grade(.7f,.9f,.96f,.06f,.96f,.35f,brown=1.4f)),
            preset("MONO_BLUE","单色蓝","单色",grade=Grade(contrast=1.12f,lift=.018f,monochrome=2)),
            preset("SUNSET_RED","赤霞红",grade=Grade(1.12f,1.06f,.94f,.012f,.985f,1.65f,magenta=1.2f)),
            preset("MOUNTAIN_CYAN","远山青",grade=Grade(.8f,.94f,1.02f,.032f,.98f,-.55f,1.35f)))
        fun valueOf(name: String)=entries.first { it.name==name }
        internal fun custom(id: String,label: String,description: String,imported: Boolean=false,loader:()->CubeLut)=
            StreetFilter(id,label,if(imported)"我的LUT" else "胶片",description,imported,loader)
    }
}
