package com.rhp.aibox.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.rhp.aibox.util.PreferenceManager
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * =====================================================================
 * ContextActivityViewModel - 系统上下文编辑页面的视图模型
 * =====================================================================
 *
 * 【为什么用 AndroidViewModel 而不是 ViewModel？】
 * AndroidViewModel 的构造函数接收 Application 实例，
 * 通过 Application.contentResolver 访问 ContentResolver 读取文件内容。
 * 普通 ViewModel 无法直接获取 Context，而 AndroidViewModel 提供了应用级别的 Context。
 *
 * 【职责】
 * - 管理系统上下文文本的编辑状态
 * - 提供从 URI 读取文件内容的方法（SAF 文件导入）
 * - 将编辑结果持久化到 SharedPreferences
 */
class ContextActivityViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * 系统上下文文本，初始值从 SharedPreferences 读取。
     * 用户在编辑框中修改时实时更新此状态。
     */
    var contextText by mutableStateOf(PreferenceManager.getSystemContext())

    /**
     * 保存当前上下文文本到 SharedPreferences。
     * 保存后，后续的 AI 对话请求将使用新的系统提示词。
     */
    fun save() {
        PreferenceManager.setSystemContext(contextText)
    }

    /**
     * 通过 ContentResolver 读取文件内容。
     *
     * 【SAF (Storage Access Framework) 工作原理】
     * 1. 用户通过系统文件选择器选择文件
     * 2. 系统返回一个 content:// URI
     * 3. ContentResolver 通过 URI 读取文件内容（支持本地存储和云存储）
     * 4. openInputStream() 返回标准 Java InputStream
     *
     * 【为什么用 Application 的 Context 而不是 Activity 的 Context？】
     * ViewModel 的生命周期比 Activity 长。使用 Application Context 避免内存泄漏。
     * ContentResolver 从 Application Context 获取完全满足文件读取需求。
     *
     * @param uri 文件 URI（content:// 格式）
     * @return 文件文本内容，读取失败返回 null
     */
    fun readTextFromUri(uri: Uri): String? {
        return try {
            val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                ?: return null
            val reader = BufferedReader(InputStreamReader(inputStream))
            val content = reader.readText()  // Kotlin 扩展函数，一次性读取全部内容
            reader.close()
            content
        } catch (e: Exception) {
            null
        }
    }
}
