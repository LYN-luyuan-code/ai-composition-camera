package com.luyuan.silkzoom

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal class LutStore(private val context: Context) {
    private val cache=java.util.concurrent.ConcurrentHashMap<String,StreetFilter>()
    private val dir=File(context.filesDir,"luts").apply { mkdirs() }
    private val online=listOf("60" to "电影60s","80" to "电影80s").map { (id,label) ->
        StreetFilter.custom("CINEMA_$id",label,"Striped Purple / Nixua，Cinema Through The Ages，MIT 授权。来源：github.com/stripedpurple/color-grading-luts；完整授权随应用提供。") {
            context.assets.open("luts/cinema$id.cube").reader().use(CubeLut::parse)
        }
    }
    fun all(): List<StreetFilter> = StreetFilter.entries+online+dir.listFiles().orEmpty().filter { it.extension=="json" }.sortedBy { it.name }.mapNotNull { file ->
        runCatching {
            val meta=JSONObject(file.readText());val id=meta.getString("id");require(id.matches(Regex("lut_[0-9a-f]{24}")))
            val cube=File(dir,"$id.cube");require(cube.isFile)
            cache.getOrPut(id) { StreetFilter.custom(id,meta.getString("label"),"导入文件：${meta.getString("filename")}\n${meta.optString("title")}\n适用于当前 SDR 取景；Log/HDR 转换 LUT 需要匹配输入色彩空间。",true) { cube.reader().use(CubeLut::parse) } }
        }.getOrNull()
    }
    fun find(name: String)=all().firstOrNull { it.name==name } ?: StreetFilter.ORIGINAL
    fun importFile(uri: Uri): StreetFilter {
        val filename=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null } ?: "自定义.cube"
        require(filename.endsWith(".cube",true)) { "请选择 .cube LUT 文件" }
        val bytes=context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer=java.io.ByteArrayOutputStream();val block=ByteArray(8192)
            while(true) { val n=input.read(block);if(n<0)break;require(buffer.size()+n<=CubeLut.MAX_BYTES){"LUT 文件超过24MB"};buffer.write(block,0,n) };buffer.toByteArray()
        } ?: error("无法读取 LUT 文件")
        val parsed=CubeLut.parse(bytes.toString(Charsets.UTF_8).reader())
        val id="lut_"+MessageDigest.getInstance("SHA-256").digest(bytes).take(12).joinToString("") { "%02x".format(it.toInt() and 255) }
        val dest=File(dir,"$id.cube")
        require(dest.exists() || dir.listFiles().orEmpty().count { it.extension=="cube" }<32) { "最多保存32个 LUT，请先删除不用的 LUT" }
        val label=(parsed.title.ifBlank { filename.removeSuffix(".cube").removeSuffix(".CUBE") }).filterNot { it.isISOControl() }.take(48).ifBlank { "自定义 LUT" }
        val temp=File.createTempFile("import-",".tmp",dir)
        try { temp.writeBytes(bytes);check(temp.renameTo(dest)){"无法保存 LUT"} } finally { temp.delete() }
        val meta=JSONObject().put("id",id).put("filename",filename).put("title",parsed.title).put("label",label)
        File(dir,"$id.json").writeText(meta.toString())
        val filter=StreetFilter.custom(id,label,"导入文件：$filename\n${parsed.title}\nSDR 创意 LUT；Log/HDR 需要匹配输入色彩空间。",true) { parsed };cache[id]=filter;return filter
    }
    fun delete(filter: StreetFilter) { require(filter.imported && filter.name.matches(Regex("lut_[0-9a-f]{24}")));cache.remove(filter.name);File(dir,"${filter.name}.cube").delete();File(dir,"${filter.name}.json").delete() }
}
