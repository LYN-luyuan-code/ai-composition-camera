package com.luyuan.silkzoom

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

internal object AiSettingsDialog {
    fun show(activity: Activity, store: AiSettingsStore, onSaved: () -> Unit): AlertDialog {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(16))
        }
        fun label(text: String) { content.addView(TextView(activity).apply { this.text = text; setPadding(0, dp(14), 0, dp(4)) }) }
        fun field(hint: String, type: Int) = EditText(activity).apply {
            this.hint = hint
            inputType = type
            isSingleLine = true
            setAutofillHints(null)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            content.addView(this)
        }
        label("AI 供应商")
        val providers = AiProvider.entries
        val spinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, providers.map { it.label })
            setSelection(providers.indexOf(store.provider))
        }
        content.addView(spinner)
        label("接口地址")
        val url = field("https://…/v1", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        label("视觉模型")
        val model = field("需支持图片输入", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        val quickModel = Button(activity).apply { text = "使用推荐快速模型" }
        content.addView(quickModel)
        label("API Key")
        val key = field("输入 API Key", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val deleteKey = CheckBox(activity).apply { text = "删除此供应商已保存的 Key" }
        content.addView(deleteKey)
        val note = TextView(activity).apply {
            text = "建议选用支持图片输入的 Flash 模型。DeepSeek / MiMo 构图请求关闭深度思考。点击 AI 构图会发送一张低分辨率取景图，可能产生 API 费用；跟踪在本地运行，Key 加密保存。"
            textSize = 12f
            setPadding(0, dp(16), 0, dp(6))
        }
        content.addView(note)
        val error = TextView(activity).apply { setTextColor(android.graphics.Color.rgb(255, 132, 132)) }
        content.addView(error)
        var loaded = store.load()
        val drafts = mutableMapOf<AiProvider, AiConfiguration>()
        val deletions = mutableMapOf<AiProvider, Boolean>()
        fun populate(config: AiConfiguration) {
            loaded = config
            quickModel.isEnabled = config.provider.defaultModel.isNotEmpty()
            url.setText(config.baseUrl)
            model.setText(config.model)
            key.setText(if (drafts.containsKey(config.provider) && config.apiKey != store.load(config.provider).apiKey) config.apiKey else "")
            key.hint = if (config.apiKey.isEmpty()) "输入 API Key" else "已保存 · 留空保留，输入替换"
            deleteKey.isChecked = deletions[config.provider] ?: false
            error.text = ""
        }
        quickModel.setOnClickListener { model.setText(loaded.provider.defaultModel) }
        populate(loaded)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val provider = providers[position]
                if (provider != loaded.provider) {
                    drafts[loaded.provider] = loaded.copy(baseUrl = url.text.toString(), model = model.text.toString(),
                        apiKey = key.text.toString().trim().ifEmpty { loaded.apiKey })
                    deletions[loaded.provider] = deleteKey.isChecked
                    populate(drafts[provider] ?: store.load(provider))
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("AI 设置")
            .setView(ScrollView(activity).apply { addView(content) })
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val config = AiConfiguration(
                    loaded.provider, url.text.toString().trim(), model.text.toString().trim(),
                    if (deleteKey.isChecked) "" else key.text.toString().trim().ifEmpty { loaded.apiKey },
                )
                try {
                    if (config.baseUrl.isNotBlank()) config.endpoint()
                    // An empty key is allowed so users can remove credentials or configure later.
                    if (config.apiKey.isNotEmpty()) config.validated()
                    store.save(config)
                    onSaved()
                    dialog.dismiss()
                } catch (failure: Exception) {
                    error.text = if (failure is IllegalArgumentException || failure is IllegalStateException) failure.message
                        else "设置保存失败，请重试"
                }
            }
        }
        dialog.show()
        return dialog
    }
}
