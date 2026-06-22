package com.rhp.aibox.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rhp.aibox.model.Message
import com.rhp.aibox.model.Role
import com.rhp.aibox.network.ChatApi
import com.rhp.aibox.util.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import java.util.UUID

/**
 * =====================================================================
 * ChatMessage - 聊天消息数据类（UI 层）
 * =====================================================================
 *
 * 与 model/Message（API 传输层）不同，ChatMessage 包含了更多 UI 需要的信息：
 * @param id        唯一标识，用于 LazyColumn 的 key（优化重组性能）
 * @param role      消息角色：USER（用户）/ ANSWER（AI回答）/ REASONING（AI推理过程）
 * @param content   消息文本内容
 * @param timestamp 消息创建时间戳
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * =====================================================================
 * Conversation - 对话会话数据类
 * =====================================================================
 *
 * 每个 Conversation 代表一个完整的对话线程。
 * @param id       唯一标识
 * @param title    对话标题（取自第一条用户消息的前20个字符）
 * @param messages 该对话中的全部消息，使用 SnapshotStateList 以支持 Compose 响应式更新
 *
 * 【SnapshotStateList】
 * 是 Compose 提供的可观察列表。当列表元素增删改时，
 * Compose 会自动重组（recomposition）使用该列表的 UI 组件。
 */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "新的对话",
    val messages: MutableList<ChatMessage> = mutableStateListOf()
)

/**
 * =====================================================================
 * MainActivityViewModel - 主界面的视图模型
 * =====================================================================
 *
 * 【ViewModel 的作用】
 * ViewModel 负责持有 UI 所需的状态数据，并在屏幕旋转等配置变更时保持数据。
 * 普通 Activity 在旋转时会重建，数据会丢失；ViewModel 的生命周期独立于 Activity。
 *
 * 【状态管理】
 * 所有被 Compose UI 观察的状态都使用 Compose State API：
 * - mutableStateOf()      : 单一可观察值
 * - mutableStateListOf()  : 可观察列表
 * - mutableIntStateOf()   : 可观察的 Int 值（优化了基本类型）
 *
 * 【线程安全】
 * handleToken/handleError/handleComplete 在主线程（Dispatchers.Main）中执行，
 * 确保 UI 更新线程安全。
 */
class MainActivityViewModel : ViewModel() {

    // ========== 对话列表 ==========
    // SnapshotStateList：当列表变化时，Compose 自动重组
    val conversations = mutableStateListOf<Conversation>()

    // ========== 当前选中的对话索引 ==========
    // mutableIntStateOf 是优化版的 mutableStateOf，专门用于 Int 类型
    var currentConversationIndex by mutableIntStateOf(-1)
        private set  // 外部可读取，只能通过 selectConversation() 修改

    // ========== 输入框文本 ==========
    var inputText by mutableStateOf("")

    // ========== 流式输出状态 ==========
    // isStreaming == true 时，发送按钮禁用，防止重复提交
    var isStreaming by mutableStateOf(false)
        private set

    // ========== 滚动触发器 ==========
    // 每次发送消息或收到 token 时更新此值，触发 LaunchedEffect 滚动到底部
    var scrollToBottomTrigger by mutableStateOf(0L)

    // ========== 当前 AI 响应起始索引 ==========
    // 标记当前轮次 AI 响应的起始位置。
    // 用于解决 REASONING 气泡复用问题：
    //   多轮对话中，新的 REASONING token 只应追加到本轮次的 REASONING 气泡，
    //   而非整个对话历史中最后一个 REASONING 气泡。
    private var currentResponseStartIndex by mutableIntStateOf(-1)

    // ========== 当前 API 请求的 Call 对象 ==========
    // 用于实现用户手动停止 AI 生成功能。
    // Call.cancel() 会关闭底层 Socket 连接，触发 onFailure 回调。
    private var currentCall: Call? = null

    // ========== 派生属性 ==========

    /** 获取当前对话的所有消息 */
    val currentMessages: List<ChatMessage>
        get() = if (currentConversationIndex in conversations.indices)
            conversations[currentConversationIndex].messages
        else emptyList()

    /** 获取当前对话对象 */
    val currentConversation: Conversation?
        get() = if (currentConversationIndex in conversations.indices)
            conversations[currentConversationIndex]
        else null

    // ========== 初始化 ==========
    init {
        val saved = PreferenceManager.loadConversations()
        if (saved.isNotEmpty()) {
            conversations.addAll(saved)
        }
        // 冷启动始终展示空白首页（新建空对话），历史对话在侧边抽屉中
        createNewConversation()
    }

    // =====================================================================
    // 对话操作
    // =====================================================================

    /**
     * 创建新的空对话。
     * 若已存在空对话则直接切换到该对话（确保同时最多一个空白对话）。
     */
    fun createNewConversation() {
        val existingEmpty = conversations.indexOfFirst { it.messages.isEmpty() }
        if (existingEmpty >= 0) {
            currentConversationIndex = existingEmpty
            return
        }
        val newConv = Conversation()
        conversations.add(0, newConv)
        currentConversationIndex = 0
    }

    /** 切换到指定索引的对话 */
    fun selectConversation(index: Int) {
        if (index in conversations.indices) {
            currentConversationIndex = index
            scrollToBottomTrigger = System.currentTimeMillis()  // 触发滚动到底部
        }
    }

    /** 删除指定索引的对话，如果删光了自动创建新对话 */
    fun deleteConversation(index: Int) {
        if (index !in conversations.indices) return
        conversations.removeAt(index)
        if (conversations.isEmpty()) {
            createNewConversation()
        } else {
            // coerceAtMost 防止索引越界
            currentConversationIndex = currentConversationIndex.coerceAtMost(conversations.lastIndex)
        }
    }

    // =====================================================================
    // 消息发送核心流程
    // =====================================================================

    /**
     * 发送用户消息并请求 AI 回复
     *
     * 【执行流程】
     * 1. 验证输入（非空、非流式中）
     * 2. 添加用户消息到对话
     * 3. 从 PreferenceManager 读取当前 API 配置
     * 4. 构建 API 消息列表（过滤掉 REASONING）
     * 5. 添加空占位消息（用于流式填充）
     * 6. 发起异步流式请求
     */
    fun sendMessage() {
        val text = inputText.trim()
        if (text.isEmpty() || isStreaming) return  // 空输入或正在流式输出中，不处理

        val conv = currentConversation ?: return

        // ----- 添加用户消息 -----
        conv.messages.add(ChatMessage(role = Role.USER, content = text))

        // ----- 第一条用户消息作为对话标题 -----
        if (conv.title == "新的对话") {
            conv.title = if (text.length > 20) text.take(20) + "..." else text
        }

        inputText = ""
        isStreaming = true
        scrollToBottomTrigger = System.currentTimeMillis()

        // ----- 从 SharedPreferences 读取用户配置的 API 参数 -----
        val apiUrl = PreferenceManager.getApiUrl()
        val apiKey = PreferenceManager.getApiKey()
        val modelName = PreferenceManager.getModelName()
        val systemContext = PreferenceManager.getSystemContext()

        // ----- 构建 API 消息列表 -----
        val apiMessages = buildApiMessages(conv)

        // ----- 添加 AI 回复占位消息（内容为空，流式填充） -----
        val aiMessage = ChatMessage(role = Role.ANSWER, content = "")
        conv.messages.add(aiMessage)

        // 记录当前轮次 AI 响应的起始索引，用于 REASONING token 的归属判断
        // 解决：第 2 轮对话的 REASONING token 误追加到第 1 轮的 REASONING 气泡
        // handleToken 只在此索引之后的范围内搜索已有的 REASONING 消息
        currentResponseStartIndex = conv.messages.lastIndex

        // ----- 发起流式请求 -----
        viewModelScope.launch {
            currentCall = ChatApi.chatStream(
                apiUrl = apiUrl,
                apiKey = apiKey,
                modelName = modelName,
                systemContext = systemContext,
                messages = apiMessages,
                onToken = { token, role ->
                    // OkHttp 回调在后台线程，用 Dispatchers.Main 切回主线程
                    viewModelScope.launch(Dispatchers.Main) {
                        handleToken(token, role)
                    }
                },
                onError = { error ->
                    viewModelScope.launch(Dispatchers.Main) {
                        handleError(error)
                    }
                },
                onComplete = {
                    viewModelScope.launch(Dispatchers.Main) {
                        handleComplete()
                    }
                }
            )
        }
    }

    /**
     * 强制停止当前 AI 生成。
     *
     * 【实现方式】
     * 调用 OkHttp Call.cancel() 立即关闭底层 Socket 连接。
     * 这会触发 ChatApi 的 onFailure 回调（IOException: Canceled），
     * 然后在 handleError 中将 isStreaming 设为 false。
     *
     * 注意：cancel() 之后不要再手动设置 isStreaming = false，
     * 交给 handleError 统一处理，避免重复清理。
     */
    fun stopMessage() {
        currentCall?.let {
            if (!it.isCanceled()) {
                it.cancel()
            }
        }
        currentCall = null
    }

    /**
     * 处理每个流式 token
     *
     * 【REASONING 处理逻辑】
     * DeepSeek R1 模型会先输出推理过程（reasoning_content），再输出正式回答。
     * 推理内容合并到同一个 REASONING 消息中，正式回答合并到 ANSWER 消息中。
     * 通过在消息列表中查找最后一个 REASONING 消息来判断是追加还是新建。
     */
    private fun handleToken(token: String, role: Role) {
        val conv = currentConversation ?: return

        when (role) {
            Role.REASONING -> {
                val messages = conv.messages
                // 仅搜索当前轮次 AI 响应范围内的 REASONING 消息
                // 避免跨轮次复用之前的 REASONING 气泡
                val searchStart = if (currentResponseStartIndex in messages.indices)
                    currentResponseStartIndex else 0
                val recentMessages = messages.subList(searchStart, messages.size)
                val reasoningMsg = recentMessages.findLast { it.role == Role.REASONING }

                if (reasoningMsg != null) {
                    // 追加到当前轮次的推理气泡
                    val idx = messages.indexOf(reasoningMsg)
                    messages[idx] = reasoningMsg.copy(content = reasoningMsg.content + token)
                } else {
                    // 新建推理消息，插入在空 ANSWER 占位之前
                    val lastMsg = messages.lastOrNull()
                    val insertIdx = if (lastMsg?.role == Role.ANSWER && lastMsg.content.isEmpty())
                        messages.lastIndex  // 插在占位消息之前
                    else
                        messages.size      // 追加到列表末尾
                    messages.add(insertIdx, ChatMessage(role = Role.REASONING, content = token))
                }
            }

            Role.ANSWER -> {
                val messages = conv.messages
                val lastMsg = messages.lastOrNull()
                if (lastMsg?.role == Role.ANSWER) {
                    // 追加到已有回答消息（流式累加）
                    messages[messages.lastIndex] = lastMsg.copy(content = lastMsg.content + token)
                } else {
                    // 新建回答消息
                    messages.add(ChatMessage(role = Role.ANSWER, content = token))
                }
            }

            Role.USER -> { /* API 不会返回 USER 角色的 token */ }
        }

        scrollToBottomTrigger = System.currentTimeMillis()  // 触发滚动
    }

    /** 处理流式请求错误，使用 ChatApi.classifyError 提供用户友好的错误提示 */
    private fun handleError(error: Throwable) {
        val conv = currentConversation ?: return
        val isCancellation = error.message?.contains("Canceled", ignoreCase = true) == true ||
                error is java.util.concurrent.CancellationException

        if (isCancellation) {
            // 用户主动停止：移除空占位或截断未完成的回答，不显示错误消息
            val lastMsg = conv.messages.lastOrNull()
            if (lastMsg?.role == Role.ANSWER && lastMsg.content.isEmpty()) {
                conv.messages.removeAt(conv.messages.lastIndex)
            }
            isStreaming = false
            PreferenceManager.saveConversations(conversations.toList())
            return
        }

        isStreaming = false
        val lastMsg = conv.messages.lastOrNull()
        val errorText = ChatApi.classifyError(error)
        if (lastMsg?.role == Role.ANSWER && lastMsg.content.isEmpty()) {
            // 用错误消息替换空占位
            conv.messages[conv.messages.lastIndex] = lastMsg.copy(content = errorText)
        } else {
            conv.messages.add(ChatMessage(role = Role.ANSWER, content = errorText))
        }
        PreferenceManager.saveConversations(conversations.toList())
    }

    /** 流式输出完成后的清理 */
    private fun handleComplete() {
        isStreaming = false
        val conv = currentConversation ?: return
        // 如果 API 没有返回任何 token（空回答），移除占位消息
        val lastMsg = conv.messages.lastOrNull()
        if (lastMsg?.role == Role.ANSWER && lastMsg.content.isEmpty()) {
            conv.messages.removeAt(conv.messages.lastIndex)
        }
        PreferenceManager.saveConversations(conversations.toList())
    }

    /**
     * 构建发送给 API 的消息列表
     *
     * 【过滤逻辑】
     * - 只包含 USER 和 ANSWER 角色的消息（REASONING 是展示用，不发给 API）
     * - 将 USER 映射为 API 的 "user" 角色
     * - 将 ANSWER 映射为 API 的 "assistant" 角色
     */
    private fun buildApiMessages(conv: Conversation): List<Message> {
        return conv.messages
            .filter { it.role == Role.USER || it.role == Role.ANSWER }
            .map {
                Message(
                    role = if (it.role == Role.USER) "user" else "assistant",
                    content = it.content
                )
            }
    }
}
