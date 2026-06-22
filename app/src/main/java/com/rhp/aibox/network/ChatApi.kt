package com.rhp.aibox.network

import com.rhp.aibox.model.ChatRequest
import com.rhp.aibox.model.ChatStreamResponse
import com.rhp.aibox.model.Message
import com.rhp.aibox.model.Role
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * =====================================================================
 * ChatApi - LLM API 通信层（基于 OkHttp 的 SSE 流式请求）
 * =====================================================================
 *
 * 【SSE (Server-Sent Events) 原理】
 * SSE 保持 HTTP 连接打开，服务器持续推送数据块（tokens），
 * 每个 token 以 "data: " 开头的一行返回，流结束时发送 "data: [DONE]"。
 *
 * 【OkHttp 客户端配置】
 * - connectTimeout: 10 秒 → 建立 TCP + TLS 连接的最大等待时间
 * - readTimeout: 0 → SSE 流式连接无读取超时（流可能持续数分钟）
 * - writeTimeout: 10 秒 → 发送请求体的超时
 * - retryOnConnectionFailure: true → 连接失败时自动重试一次
 *
 * 【为什么不用 Retrofit】
 * Retrofit 适合"请求-响应"模式，SSE 需要手动逐行读取响应流
 * （response.body.source()），OkHttp 提供更灵活的底层流控制。
 */
object ChatApi {

    // ========== OkHttp 客户端（单例，复用连接池和线程池） ==========
    // lazy 延迟初始化，避免 DNS 配置在类加载时执行
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)    // TCP + TLS 握手超时
            .readTimeout(0, TimeUnit.MILLISECONDS)    // SSE 无读取超时
            .writeTimeout(10, TimeUnit.SECONDS)       // 请求体发送超时
            .retryOnConnectionFailure(true)           // 连接失败自动重试 1 次
            .dns(DiagnosticDns())                     // 自定义 DNS（含诊断信息）
            .build()
    }

    // ========== Gson 序列化/反序列化工具 ==========
    private val gson = Gson()

    /**
     * =====================================================================
     * DiagnosticDns - 诊断型 DNS 解析器
     * =====================================================================
     *
     * 包装系统 DNS，在解析失败时提供更详细的错误信息。
     * 帮助用户区分"无网络连接"、"DNS 服务器不可达"、"主机名不存在"等情况。
     *
     * 【常见 DNS 失败原因】
     * 1. 无网络连接 → 系统 DNS 返回 UnknownHostException
     * 2. 代理/VPN 拦截 → DNS 查询被代理丢弃
     * 3. 防火墙阻止 → DNS 端口 (53) 被屏蔽
     * 4. 主机名拼写错误 → 域名不存在
     */
    private class DiagnosticDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                // 检查是否有网络连接（简单判断：尝试解析一个知名域名）
                Dns.SYSTEM.lookup(hostname).also {
                    if (it.isEmpty()) {
                        throw UnknownHostException("DNS 解析返回空结果: $hostname")
                    }
                }
            } catch (e: UnknownHostException) {
                // 包装异常，添加更详细的信息
                throw UnknownHostException(
                    "无法解析服务器地址「$hostname」。" +
                    "请检查：\n" +
                    "1. 设备是否已连接网络\n" +
                    "2. VPN/代理是否干扰 DNS 解析\n" +
                    "3. API 地址是否正确\n" +
                    "原始错误: ${e.message}"
                )
            }
        }
    }

    // =====================================================================
    // 错误信息分类（帮助 UI 层显示更友好的提示）
    // =====================================================================

    /** 将技术异常转换为用户可读的错误消息 */
    fun classifyError(e: Throwable): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("Unable to resolve host") || msg.contains("无法解析服务器") ->
                "无法连接到服务器，请检查网络连接和 API 地址"
            msg.contains("timeout") || msg.contains("timed out") ->
                "连接超时，请检查网络状况或更换 API 地址"
            msg.contains("401") || msg.contains("403") ->
                "API Key 无效或权限不足，请检查密钥配置"
            msg.contains("429") ->
                "请求过于频繁，请稍后再试"
            msg.contains("500") || msg.contains("502") || msg.contains("503") ->
                "服务器暂时不可用，请稍后重试"
            msg.contains("certificate") || msg.contains("SSL") ->
                "SSL 证书验证失败，请检查 API 地址是否正确"
            msg.contains("No address associated") ->
                "DNS 解析失败，请检查：\n1. 设备网络是否正常\n2. 是否开启了 VPN/代理\n3. API 地址拼写是否正确"
            else ->
                "网络错误: $msg"
        }
    }

    /**
     * =====================================================================
     * chatStream - 发起流式对话请求
     * =====================================================================
     *
     * @param apiUrl       API 端点（如 https://api.deepseek.com/v1/chat/completions）
     * @param apiKey       Bearer Token 认证密钥
     * @param modelName    模型名称（如 "deepseek-chat"）
     * @param systemContext 系统提示词（System Prompt）
     * @param messages     对话历史（USER + ASSISTANT）
     * @param onToken      每收到一个 token 的回调 (token, role)
     * @param onError      出错回调（网络错误 / HTTP 错误 / 解析错误）
     * @param onComplete   流正常结束回调
     */
    fun chatStream(
        apiUrl: String,
        apiKey: String,
        modelName: String,
        systemContext: String,
        messages: List<Message>,
        onToken: (String, Role) -> Unit,
        onError: (Throwable) -> Unit,
        onComplete: () -> Unit
    ): Call {
        // ========== 构建请求体 ==========
        // 将系统上下文作为第一条 system 消息添加到对话最前面
        val fullMessages = listOf(
            Message("system", systemContext)  // System Prompt 定义 AI 行为
        ) + messages

        val requestBodyObj = ChatRequest(
            model = modelName,   // 用户选择的模型
            messages = fullMessages,
            stream = true        // 启用流式输出（SSE）
        )

        // ========== 序列化并构建 HTTP 请求 ==========
        val json = gson.toJson(requestBodyObj)
        val requestBody = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer $apiKey")  // Bearer Token 认证
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        // ========== 发起异步请求，返回 Call 供调用方取消 ==========
        val call = client.newCall(request)
        call.enqueue(object : okhttp3.Callback {

            /**
             * 网络请求失败回调（如 DNS 解析失败、连接超时、无网络等）
             */
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onError(e)
            }

            /**
             * 服务器响应回调
             * 注意：即使 HTTP 状态码是 200，这也代表"连接建立成功"，
             * 真正的响应体需要通过 source 逐行读取。
             */
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {

                // HTTP 错误处理（401=API Key 无效, 429=频率限制, 500=服务器错误 等）
                if (!response.isSuccessful) {
                    onError(IOException("HTTP ${response.code}"))
                    return
                }

                // 获取响应体的"源流"，用于逐行读取 SSE 数据
                val source = response.body?.source() ?: return

                try {
                    // ----- 循环读取 SSE 数据，直到流结束 -----
                    while (!source.exhausted()) {

                        // readUtf8Line()：读取一行文本，遇到 \n 停止
                        // 返回 null 表示流意外结束（连接断开），安全退出循环
                        val line = source.readUtf8Line() ?: continue

                        // SSE 协议：只有以 "data: " 开头的行才包含有效数据
                        // 空行和注释行（":" 开头）用于保持连接心跳（keep-alive），直接跳过
                        if (!line.startsWith("data:")) continue

                        // 去掉 "data: " 前缀（6个字符），得到纯 JSON 字符串
                        val data = line.removePrefix("data: ").trim()

                        // SSE 协议规范：[DONE] 表示数据流正常结束
                        // 服务器可能发送 "data: [DONE]" 或 "data:[DONE]"
                        if (data == "[DONE]") {
                            onComplete()
                            break
                        }

                        // ----- 解析 JSON 为 ChatStreamResponse 对象 -----
                        val streamResponse =
                            gson.fromJson(data, ChatStreamResponse::class.java)

                        val delta = streamResponse.choices
                            .firstOrNull()  // 取第一个 choice（通常只有一个）
                            ?.delta

                        // DeepSeek R1 模型特有：推理过程（chain-of-thought）
                        delta?.reasoning_content?.let {
                            onToken(it, Role.REASONING)
                        }

                        // 正式回答内容
                        delta?.content?.let {
                            onToken(it, Role.ANSWER)
                        }
                    }

                } catch (e: Exception) {
                    // 解析异常（JSON 格式错误、网络中断等）
                    onError(e)
                } finally {
                    // 主动关闭响应体，释放底层 Socket 连接
                    response.close()
                }
            }
        })
        return call
    }
}
