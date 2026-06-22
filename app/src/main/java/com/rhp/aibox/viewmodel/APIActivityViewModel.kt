package com.rhp.aibox.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rhp.aibox.network.ChatApi
import com.rhp.aibox.util.PreferenceManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.IOException

/**
 * =====================================================================
 * APIActivityViewModel - API 配置页面的视图模型
 * =====================================================================
 *
 * 【职责】
 * 集中管理 API 设置页面的所有 UI 状态和业务逻辑：
 * - 平台选择（DeepSeek / 智谱AI / 阿里云百炼）
 * - API URL 和 API Key 编辑
 * - 模型选择（预设列表 + 自定义输入）
 * - 从 API 获取可用模型列表（异步网络请求）
 * - 保存配置到 SharedPreferences
 *
 * 【状态列表】
 * 共管理 9 个状态字段 + 1 个动态列表，全部使用 Compose State API
 * 以便 Compose UI 自动响应数据变化。
 */
class APIActivityViewModel : ViewModel() {

    // ========== 平台与 API 配置 ==========

    /** 当前选中的平台标识（"deepseek"/"zhipu"/"aliyun"） */
    var selectedPlatform by mutableStateOf(PreferenceManager.getPlatform())

    /** API 端点 URL，可手动修改 */
    var apiUrl by mutableStateOf(PreferenceManager.getApiUrl())

    /** API 密钥 — 初始加载当前平台的已保存 Key */
    var apiKey by mutableStateOf(PreferenceManager.getApiKey())

    /** 当前选中的模型名称 */
    var modelName by mutableStateOf(PreferenceManager.getModelName())

    // ========== UI 辅助状态 ==========

    /** API Key 是否以明文显示 */
    var passwordVisible by mutableStateOf(false)

    /** 模型选择对话框是否可见 */
    var showModelDialog by mutableStateOf(false)

    /** 自定义模型输入框的文本 */
    var customModelInput by mutableStateOf("")

    /** 从 API 获取到的模型列表（使用 SnapshotStateList 以支持 Compose 响应式更新） */
    val fetchedModels = mutableStateListOf<String>()

    /** 是否正在从 API 获取模型列表（控制按钮的 enabled 和文字显示） */
    var isFetchingModels by mutableStateOf(false)

    /** 获取模型列表的结果消息（非空时 UI 弹出 Toast 并重置为 null） */
    var fetchResultMessage by mutableStateOf<String?>(null)

    // =====================================================================
    // 平台切换
    // =====================================================================

    /**
     * 切换平台时自动更新配置。
     *
     * 【逻辑】
     * 1. 保存当前平台的 API Key（编辑中可能尚未保存，避免切换丢失）
     * 2. 保存当前平台的模型名（同上）
     * 3. 更新 selectedPlatform 状态
     * 4. 从 PreferenceManager.platforms 获取预设 apiUrl
     * 5. 加载新平台已保存的模型和 API Key（每个平台独立存储）
     * 6. 清空之前从其他平台获取的模型列表
     */
    fun onPlatformChange(key: String) {
        // 保存当前平台编辑中的值（切换不丢失）
        val oldPlatform = selectedPlatform
        if (apiKey.isNotBlank()) {
            PreferenceManager.setApiKeyFor(oldPlatform, apiKey)
        }
        PreferenceManager.setModelNameFor(oldPlatform, modelName)

        selectedPlatform = key
        val config = PreferenceManager.platforms[key] ?: return
        apiUrl = config.apiUrl
        // 加载新平台已保存的模型和 Key（首次使用自动回退到默认模型）
        modelName = PreferenceManager.getModelNameFor(key)
        apiKey = PreferenceManager.getApiKeyFor(key)
        fetchedModels.clear()
    }

    // =====================================================================
    // 持久化
    // =====================================================================

    /** 将所有编辑值写入 SharedPreferences（Key 和模型按平台分别存储） */
    fun save() {
        PreferenceManager.switchPlatform(selectedPlatform)
        PreferenceManager.setApiUrl(apiUrl)
        PreferenceManager.setApiKeyFor(selectedPlatform, apiKey)
        PreferenceManager.setModelNameFor(selectedPlatform, modelName)
    }

    // =====================================================================
    // 从 API 获取模型列表
    // =====================================================================

    /**
     * 异步调用平台 API 获取可用模型列表。
     *
     * 【执行线程】
     * 网络请求在 Dispatchers.IO 上执行，不阻塞 UI 线程。
     * 结果写回主线程的 Compose State，自动触发 UI 重组。
     *
     * 【API 端点推导】
     * 从用户配置的 chat/completions URL 推导 models 端点：
     * https://api.deepseek.com/v1/chat/completions → https://api.deepseek.com/v1/models
     *
     * 【响应格式（OpenAI 兼容）】
     * { "data": [{"id": "model-1"}, {"id": "model-2"}, ...] }
     */
    fun fetchModelsFromAPI() {
        isFetchingModels = true
        viewModelScope.launch(Dispatchers.IO) {
            var models = emptyList<String>()
            var errorMsg: String? = null

            try {
                models = fetchModelsFromNetwork()
            } catch (e: IOException) {
                errorMsg = ChatApi.classifyError(e)
            } catch (e: Exception) {
                errorMsg = ChatApi.classifyError(e)
            }

            // 切回主线程更新 UI 状态
            launch(Dispatchers.Main) {
                fetchedModels.clear()
                fetchedModels.addAll(models)
                isFetchingModels = false

                // 反馈结果给用户
                fetchResultMessage = when {
                    errorMsg != null -> errorMsg
                    models.isEmpty() -> "未获取到模型，请检查 API Key 和网络连接"
                    else -> "成功获取 ${models.size} 个模型"
                }
            }
        }
    }

    /**
     * 执行实际的网络请求获取模型列表。
     * 从当前配置的 apiUrl 推导 models 端点，发 GET 请求并解析响应。
     *
     * 【API 端点推导】
     * https://api.deepseek.com/v1/chat/completions → https://api.deepseek.com/v1/models
     *
     * 【响应格式】
     * {"data": [{"id": "model-1"}, {"id": "model-2"}, ...]}
     */
    private fun fetchModelsFromNetwork(): List<String> {
        val modelsUrl = apiUrl
            .removeSuffix("/chat/completions")
            .removeSuffix("/") + "/models"

        val request = Request.Builder()
            .url(modelsUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val response = ChatApi.client.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()

        val body = response.body?.string() ?: return emptyList()

        val json = Gson().fromJson(body, Map::class.java)
        val data = json["data"] as? List<*> ?: return emptyList()

        return data.mapNotNull { item ->
            (item as? Map<*, *>)?.get("id") as? String
        }
    }
}
