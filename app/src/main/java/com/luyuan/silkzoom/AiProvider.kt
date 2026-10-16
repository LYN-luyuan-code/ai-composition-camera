package com.luyuan.silkzoom

import java.net.URI
import java.net.URL

internal enum class AiProvider(val label: String, val baseUrl: String, val defaultModel: String) {
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-flash"),
    MIMO("小米 MiMo", "https://api.xiaomimimo.com/v1", "mimo-v2.6-flash"),
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4.1-mini"),
    DASHSCOPE("阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3-vl-plus"),
    SILICONFLOW("硅基流动", "https://api.siliconflow.cn/v1", "Pro/moonshotai/Kimi-K2.6"),
    CUSTOM("自定义兼容接口", "", "");
}

internal data class AiConfiguration(
    val provider: AiProvider,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
) {
    fun endpoint(): URL {
        val uri = runCatching { URI(baseUrl.trim()) }.getOrNull()
        require(uri != null && uri.scheme == "https" && !uri.host.isNullOrEmpty() &&
            uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "请输入 HTTPS 接口地址，不要在地址中填写密钥或参数"
        }
        val base = uri.toString().trimEnd('/')
        return URI(if (base.endsWith("/chat/completions")) base else "$base/chat/completions").toURL()
    }

    fun validated(): AiConfiguration {
        endpoint()
        require(model.isNotBlank() && model.length <= 200) { "请填写支持图片输入的模型名称" }
        require(apiKey.isNotBlank() && apiKey.none { it == '\r' || it == '\n' }) { "请填写有效的 API Key" }
        return this
    }
}
