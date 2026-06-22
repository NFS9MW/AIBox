package com.rhp.aibox.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * =====================================================================
 * 主题颜色定义
 * =====================================================================
 *
 * 【命名规范】
 * 数字后缀表示亮色/暗色主题：
 * - 80：暗色主题使用的颜色（较亮的色调，在暗色背景下有足够对比度）
 * - 40：亮色主题使用的颜色（较暗的色调，在亮色背景下保持可读性）
 *
 * 这种命名方式源自 Material Design 的色调系统（tonal palette）：
 * 色调值 0-100，数字越大颜色越亮。
 *
 * 【Material3 颜色角色说明】
 * - primary:   主要品牌色（FAB 按钮、选中状态、强力强调元素、主按钮）
 * - secondary: 次要品牌色（次要按钮、筛选芯片、装饰性元素）
 * - tertiary:  第三品牌色（对比强调、特殊高亮，用于与 primary/secondary 区分的设计元素）
 *
 * 【当前配色方案】
 * 以紫色（Purple）为主色调，紫灰色为辅，粉红色为第三色。
 * 这是一个通用的中性配色，适合 AI 助手类应用的科技感定位。
 *
 * 【自定义指南】
 * 如需更改主题色，只需修改下面 6 个颜色的十六进制值即可。
 * 推荐使用 Material Theme Builder (https://m3.material.io/theme-builder)
 * 生成完整的色调系统。
 */

// ===== 暗色主题配色（较亮的色调，适合暗背景） =====
val Purple80 = Color(0xFFD0BCFF)     // 淡紫色 - primary
val PurpleGrey80 = Color(0xFFCCC2DC) // 淡紫灰色 - secondary
val Pink80 = Color(0xFFEFB8C8)       // 淡粉色 - tertiary

// ===== 亮色主题配色（较暗的色调，适合亮背景） =====
val Purple40 = Color(0xFF6650a4)     // 深紫色 - primary
val PurpleGrey40 = Color(0xFF625b71) // 深紫灰色 - secondary
val Pink40 = Color(0xFF7D5260)       // 深粉色 - tertiary
