package com.rhp.aibox.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.rhp.aibox.util.PreferenceManager

/**
 * =====================================================================
 * SettingsActivityViewModel - 设置页面的视图模型
 * =====================================================================
 *
 * 【职责】
 * 管理设置页面的所有 UI 状态：
 * - 深色模式选择（themeMode）
 * - API 设置卡片副标题（platform + model）
 * - 系统上下文卡片预览文本
 *
 * 【状态刷新】
 * onResume() 在每次从子页面返回时调用，刷新卡片显示值以确保
 * APIActivity / ContextActivity 中的修改能立即反映到 SettingsActivity。
 */
class SettingsActivityViewModel : ViewModel() {

    // ========== 深色模式 ==========

    private var _themeMode by mutableIntStateOf(PreferenceManager.getThemeMode())
    val themeMode: Int get() = _themeMode

    // ========== 卡片显示状态（Compose State，变化时自动重组） ==========

    /** API 设置卡片副标题，格式："平台名 · 模型名" */
    var apiCardSubtitle by mutableStateOf("")
        private set

    /** 系统上下文卡片预览文本（截取前 50 字符） */
    var contextCardPreview by mutableStateOf("")
        private set

    // ========== 初始化 ==========

    init {
        refreshCardValues()
    }

    // ========== 公共方法 ==========

    /** 从子页面返回时调用，刷新卡片显示的最新配置值 */
    fun onResume() {
        refreshCardValues()
    }

    /**
     * 设置主题模式并持久化。
     * @param mode 0=跟随系统, 1=强制浅色, 2=强制深色
     */
    fun setThemeMode(mode: Int) {
        _themeMode = mode
        PreferenceManager.setThemeMode(mode)
    }

    // ========== 内部方法 ==========

    /** 从 PreferenceManager 读取最新配置并更新卡片状态 */
    private fun refreshCardValues() {
        val config = PreferenceManager.getCurrentPlatformConfig()
        apiCardSubtitle = "${config.displayName} · ${PreferenceManager.getModelName()}"
        val ctx = PreferenceManager.getSystemContext()
        contextCardPreview = if (ctx.length > 50) ctx.take(50) + "..." else ctx
    }
}
