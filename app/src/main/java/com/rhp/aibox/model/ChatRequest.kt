package com.rhp.aibox.model

/**
 * =====================================================================
 * ChatRequest - LLM API 请求体数据模型
 * =====================================================================
 *
 * 该类序列化为 JSON 后作为 HTTP POST 请求的 body 发送给 LLM API。
 *
 * 【字段说明】
 * @param model    模型名称（如 "deepseek-chat", "glm-4-flash"）
 * @param messages 对话消息列表，按时间顺序排列
 * @param stream   是否启用流式输出（SSE）。true = 逐 token 返回，false = 一次性返回完整响应
 *
 * 【序列化】
 * Gson 使用反射将 data class 自动序列化为 JSON：
 * {"model":"deepseek-chat","messages":[...],"stream":true}
 */
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false  // 默认不流式，需显式启用
)

/**
 * =====================================================================
 * Message - API 通信层的单条消息
 * =====================================================================
 *
 * 【角色规范（OpenAI 兼容格式）】
 * - "system":    系统级指令，定义 AI 的行为和角色（每条对话的首条消息）
 * - "user":      用户输入
 * - "assistant": AI 的历史回复（用于多轮对话上下文）
 *
 * 【与 ChatMessage（UI 层）的区别】
 * Message 用于 API 传输，ChatMessage 用于 UI 展示。
 * ChatMessage 有额外的 id、timestamp、UI 专用 Role 枚举等字段。
 */
data class Message(
    val role: String,    // "system" | "user" | "assistant"
    val content: String  // 消息文本内容
)
