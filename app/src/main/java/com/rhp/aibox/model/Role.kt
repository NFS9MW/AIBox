package com.rhp.aibox.model

/**
 * =====================================================================
 * Role - 消息角色枚举（UI 层使用）
 * =====================================================================
 *
 * 【三种角色】
 * - USER:      用户输入的消息，显示在右侧气泡
 * - REASONING: AI 的深度推理过程（DeepSeek R1 特有），显示在左侧，半透明斜体样式
 * - ANSWER:    AI 的正式回答，显示在左侧，使用 Markdown 渲染
 *
 * 【为什么不用 API 的 "system"/"user"/"assistant"】
 * API 层的角色是字符串（"system", "user", "assistant"），
 * 但 UI 层需要区分 REASONING（推理过程）和 ANSWER（正式回答），
 * 这两种在 API 中都属于 "assistant"，所以需要额外的枚举来区分。
 *
 * 【使用】
 * ChatMessage.role 使用此枚举；
 * Message.role（API 层）使用字符串 "user"/"assistant"。
 */
enum class Role {
    USER,       // 用户消息
    REASONING,  // AI 推理/思考过程
    ANSWER      // AI 正式回答
}
