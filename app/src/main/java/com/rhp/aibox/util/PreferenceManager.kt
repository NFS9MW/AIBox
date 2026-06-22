package com.rhp.aibox.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import com.rhp.aibox.MyApplication
import com.rhp.aibox.viewmodel.ChatMessage
import com.rhp.aibox.viewmodel.Conversation
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * =====================================================================
 * PreferenceManager - 应用设置持久化管理器
 * =====================================================================
 *
 * 【SharedPreferences 简介】
 * SharedPreferences 是 Android 内置的轻量级键值对存储方案，适合保存少量配置数据。
 * 数据以 XML 文件形式存储在 /data/data/<包名>/shared_prefs/ 目录下。
 * - apply()：异步写入磁盘（推荐，不会阻塞 UI 线程）
 * - commit()：同步写入磁盘（会返回成功/失败，但会阻塞线程）
 *
 * 【本类的设计思路】
 * 使用 Kotlin object 单例模式，通过 MyApplication 提供的全局 Context 来访问
 * SharedPreferences，避免了在每个调用处传递 Context 的麻烦。
 *
 * 【数据键说明】
 * KEY_THEME_MODE    : 深色模式设置（0=跟随系统, 1=浅色模式, 2=深色模式）
 * KEY_API_URL       : API 请求地址（根据平台自动切换）
 * KEY_SYSTEM_CONTEXT: 系统上下文（system prompt）
 * KEY_PLATFORM      : 当前选择的平台标识（deepseek / zhipu / aliyun）
 * 模型名称 & API Key : 按平台分别存储（model_name_<platform> / api_key_<platform>）
 */
object PreferenceManager {

    // ========== SharedPreferences 文件名 ==========
    // 这个名称会作为 XML 文件名，通常用包名或应用名命名
    private const val PREFS_NAME = "aibox_settings"

    // ========== 存储键常量 ==========
    // 使用 const val 定义在编译时就能确定的常量，避免多次创建字符串
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_API_URL = "api_url"
    private const val KEY_SYSTEM_CONTEXT = "system_context"
    private const val KEY_PLATFORM = "platform"

    // ========== 默认值常量 ==========
    private const val DEFAULT_THEME_MODE = 0              // 默认跟随系统
    private const val DEFAULT_PLATFORM = "deepseek"       // 默认 DeepSeek 平台
    private const val DEFAULT_SYSTEM_CONTEXT = "You are a helpful assistant"

    /**
     * =====================================================================
     * 平台配置数据类
     * =====================================================================
     *
     * 每个平台包含：
     * @param displayName 在 UI 中显示的中文名称
     * @param apiUrl      完整的 Chat Completions API 地址
     * @param defaultModel 该平台的默认模型
     * @param presetModels 该平台提供的常用模型列表
     */
    data class PlatformConfig(
        val displayName: String,
        val apiUrl: String,
        val defaultModel: String,
        val presetModels: List<String>
    )

    /**
     * =====================================================================
     * 平台预设配置表
     * =====================================================================
     *
     * 使用 Map<String, PlatformConfig> 存储所有支持的平台。
     * Key 是平台标识符（用于 KEY_PLATFORM 存储），Value 是平台配置。
     *
     * 支持的平台：
     * 1. DeepSeek（深度求索）  - https://api.deepseek.com
     * 2. 智谱 AI（GLM 系列）   - https://open.bigmodel.cn
     * 3. 阿里云百炼（Qwen 系列）- https://dashscope.aliyuncs.com
     */
    val platforms: Map<String, PlatformConfig> = mapOf(
        // ----- DeepSeek 深度求索 -----
        "deepseek" to PlatformConfig(
            displayName = "DeepSeek",
            apiUrl = "https://api.deepseek.com/v1/chat/completions",
            defaultModel = "deepseek-chat",
            presetModels = listOf(
                "deepseek-chat",       // 通用对话模型（V3）
                "deepseek-reasoner"    // 推理增强模型（R1，支持深度思考）
            )
        ),
        // ----- 智谱 AI -----
        "zhipu" to PlatformConfig(
            displayName = "智谱AI",
            apiUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            defaultModel = "glm-4-flash",
            presetModels = listOf(
                "glm-4-flash",        // 免费快速模型
                "glm-4",              // 标准模型
                "glm-4-plus",         // 增强模型
                "glm-4-long"          // 长上下文模型（128K）
            )
        ),
        // ----- 阿里云百炼 -----
        "aliyun" to PlatformConfig(
            displayName = "阿里云百炼",
            apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            defaultModel = "qwen-turbo",
            presetModels = listOf(
                "qwen-turbo",                // 快速经济模型
                "qwen-plus",                 // 增强模型
                "qwen-max",                  // 最强模型
                "qwen-max-longcontext"       // 长上下文模型
            )
        )
    )

    // ========== 延迟初始化 ==========
    // lazy 确保 SharedPreferences 实例只在第一次访问时创建
    private var prefs: SharedPreferences? = null

    /**
     * 初始化方法 —— 必须在 Application.onCreate() 中调用
     * @param context Application 级别的 Context，避免 Activity 内存泄漏
     */
    fun init(context: Context) {
        // getSharedPreferences(name, mode):
        //   name: 文件名
        //   mode: Context.MODE_PRIVATE 表示只有本应用可以访问
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 安全的 prefs 访问，防止未初始化时报 NullPointerException */
    private fun prefs(): SharedPreferences {
        return prefs ?: MyApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // =====================================================================
    // 主题模式
    // =====================================================================

    /** 获取深色模式设置：0=跟随系统, 1=浅色, 2=深色 */
    fun getThemeMode(): Int = prefs().getInt(KEY_THEME_MODE, DEFAULT_THEME_MODE)

    /** 设置深色模式并立即持久化 */
    fun setThemeMode(mode: Int) {
        prefs().edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    // =====================================================================
    // API Key（按平台分别存储）
    // =====================================================================
    //
    // 每个平台的 API Key 独立存储，切换平台时自动加载对应的 Key。
    // 存储键格式: api_key_<platform>
    // 例如: api_key_deepseek, api_key_zhipu, api_key_aliyun

    /**
     * 获取当前平台的 API Key。
     * 自动根据 KEY_PLATFORM 读取对应的平台 Key。
     */
    fun getApiKey(): String {
        val platform = getPlatform()
        return getApiKeyFor(platform)
    }

    /**
     * 获取指定平台的 API Key
     * @param platform 平台标识（deepseek / zhipu / aliyun）
     */
    fun getApiKeyFor(platform: String): String {
        val key = "api_key_$platform"
        return prefs().getString(key, "") ?: ""
    }

    /**
     * 保存当前平台的 API Key。
     * 自动根据 KEY_PLATFORM 写入对应的平台 Key。
     */
    fun setApiKey(key: String) {
        val platform = getPlatform()
        setApiKeyFor(platform, key)
    }

    /**
     * 保存指定平台的 API Key
     * @param platform 平台标识
     * @param key      API Key 值
     */
    fun setApiKeyFor(platform: String, key: String) {
        val prefKey = "api_key_$platform"
        prefs().edit().putString(prefKey, key).apply()
    }

    // =====================================================================
    // API URL
    // =====================================================================

    /** 获取 API URL，默认使用 DeepSeek 的地址 */
    fun getApiUrl(): String {
        val savedUrl = prefs().getString(KEY_API_URL, "")
        return if (savedUrl.isNullOrBlank()) {
            platforms[DEFAULT_PLATFORM]?.apiUrl ?: ""
        } else {
            savedUrl
        }
    }

    /** 保存 API URL */
    fun setApiUrl(url: String) {
        prefs().edit().putString(KEY_API_URL, url).apply()
    }

    // =====================================================================
    // 模型名称（按平台分别存储，同 API Key 模式）
    // =====================================================================
    //
    // 存储键格式: model_name_<platform>
    // 例如: model_name_deepseek, model_name_zhipu, model_name_aliyun

    /**
     * 获取当前平台的模型名称。
     * 如果该平台从未保存过模型，回退到该平台的默认模型。
     */
    fun getModelName(): String {
        val platform = getPlatform()
        return getModelNameFor(platform)
    }

    /**
     * 获取指定平台的模型名称
     * @param platform 平台标识（deepseek / zhipu / aliyun）
     * @return 已保存的模型名，若未保存则返回该平台的默认模型
     */
    fun getModelNameFor(platform: String): String {
        val key = "model_name_$platform"
        val saved = prefs().getString(key, "")
        if (!saved.isNullOrBlank()) return saved
        return platforms[platform]?.defaultModel ?: "deepseek-chat"
    }

    /**
     * 保存当前平台的模型名称。
     * 自动根据 KEY_PLATFORM 写入对应的平台 Key。
     */
    fun setModelName(model: String) {
        val platform = getPlatform()
        setModelNameFor(platform, model)
    }

    /**
     * 保存指定平台的模型名称
     * @param platform 平台标识
     * @param model    模型名称
     */
    fun setModelNameFor(platform: String, model: String) {
        val key = "model_name_$platform"
        prefs().edit().putString(key, model).apply()
    }

    // =====================================================================
    // 平台标识
    // =====================================================================

    /** 获取当前平台标识 */
    fun getPlatform(): String = prefs().getString(KEY_PLATFORM, DEFAULT_PLATFORM) ?: DEFAULT_PLATFORM

    /** 保存当前平台标识 */
    fun setPlatform(platform: String) {
        prefs().edit().putString(KEY_PLATFORM, platform).apply()
    }

    // =====================================================================
    // 系统上下文（System Prompt）
    // =====================================================================

    /**
     * 获取系统上下文
     *
     * 系统上下文（System Prompt）是 LLM API 中的一个特殊消息角色，
     * 用于设定 AI 助手的行为、角色、知识范围和回答风格。
     * 它会在每次 API 请求时作为第一条消息发送给模型。
     */
    fun getSystemContext(): String =
        prefs().getString(KEY_SYSTEM_CONTEXT, DEFAULT_SYSTEM_CONTEXT) ?: DEFAULT_SYSTEM_CONTEXT

    /** 保存系统上下文 */
    fun setSystemContext(context: String) {
        prefs().edit().putString(KEY_SYSTEM_CONTEXT, context).apply()
    }

    // =====================================================================
    // 便捷方法
    // =====================================================================

    /**
     * 获取当前平台的完整配置
     * 如果平台标识在预设中找不到，回退到 DeepSeek
     */
    fun getCurrentPlatformConfig(): PlatformConfig {
        return platforms[getPlatform()] ?: platforms[DEFAULT_PLATFORM]!!
    }

    /**
     * 切换平台时只持久化平台标识和 API URL。
     * 模型名称由 ViewModel.onPlatformChange() 通过 getModelNameFor() 读取，
     * 保存时通过 setModelNameFor() 写入，避免覆盖已保存的模型。
     */
    fun switchPlatform(platform: String) {
        val config = platforms[platform] ?: return
        setPlatform(platform)
        setApiUrl(config.apiUrl)
    }

    // =====================================================================
    // 对话记录持久化（最新 10 条）
    // =====================================================================
    //
    // Conversation 通过 Gson 序列化为 JSON 存储。
    // 反序列化时 Gson 创建 ArrayList（非 SnapshotStateList），
    // 需要手动转换为 mutableStateListOf 以确保 Compose 响应式更新。

    private const val KEY_CONVERSATIONS = "conversations_json"

    /**
     * 保存对话列表到 SharedPreferences（JSON 格式）。
     *
     * 【过滤与限制】
     * - 只保存有消息的非空对话
     * - 最多保存最新 10 条，超出部分丢弃
     * - 调用时机：每次 AI 回复完成或出错后（handleComplete / handleError）
     */
    fun saveConversations(conversations: List<Conversation>) {
        val nonEmpty = conversations.filter { it.messages.isNotEmpty() }
        val gson = Gson()
        val json = gson.toJson(nonEmpty.take(10))
        prefs().edit().putString(KEY_CONVERSATIONS, json).apply()
    }

    /**
     * 从 SharedPreferences 加载对话列表。
     *
     * 【SnapshotStateList 转换】
     * Gson 反序列化 List 时创建 ArrayList，但 Conversation.messages
     * 必须是 SnapshotStateList 才能让 Compose 追踪其变化。
     * 因此加载后手动将每个 Conversation 的 messages 转换为 mutableStateListOf。
     *
     * @return 对话列表，解析失败时返回空列表
     */
    fun loadConversations(): List<Conversation> {
        val json = prefs().getString(KEY_CONVERSATIONS, "") ?: ""
        if (json.isBlank()) return emptyList()
        return try {
            val gson = Gson()
            val type = object : TypeToken<List<Conversation>>() {}.type
            val loaded: List<Conversation> = gson.fromJson(json, type)
            loaded.map { conv ->
                Conversation(
                    id = conv.id,
                    title = conv.title,
                    messages = mutableStateListOf(*conv.messages.toTypedArray())
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
