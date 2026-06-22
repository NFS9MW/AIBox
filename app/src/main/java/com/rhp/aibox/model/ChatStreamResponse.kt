package com.rhp.aibox.model

/**
 * =====================================================================
 * ChatStreamResponse - SSE 流式响应的 JSON 数据模型
 * =====================================================================
 *
 * 【SSE 响应格式】
 * 服务器每行返回一个 JSON 块，格式为：
 * data: {"choices":[{"delta":{"content":"你","reasoning_content":""}}],"id":"chatcmpl-xxx","model":"deepseek-chat"}
 *
 * 【解析路径】
 * ChatStreamResponse → choices[0] → delta → content / reasoning_content
 *
 * 【reasoning_content 说明】
 * DeepSeek R1 等推理模型会在正式回答前输出推理过程。
 * reasoning_content 包含思维链（chain-of-thought），
 * content 包含最终回答内容。
 * 两者以独立的 SSE chunk 返回，不会同时出现在同一块中。
 */
data class ChatStreamResponse(
    val choices: List<StreamChoice>  // 选择列表（通常只有一个）
)

/**
 * 单个选择项
 */
data class StreamChoice(
    val delta: Delta  // 增量内容（流式模式下每次只返回一个 token 的增量）
)

/**
 * 增量内容体
 *
 * @param content           正式回答的文本增量
 * @param reasoning_content 推理过程的文本增量（R1 模型特有）。为 null 时表示该 chunk 不含推理内容
 */
data class Delta(
    val content: String?,
    val reasoning_content: String?
)
