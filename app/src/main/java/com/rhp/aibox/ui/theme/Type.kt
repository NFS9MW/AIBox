package com.rhp.aibox.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * =====================================================================
 * Typography - Material3 排版系统配置
 * =====================================================================
 *
 * 【Typography 的作用】
 * Typography 定义了应用中所有文本的样式层级（类似 CSS 的 heading/body/caption），
 * 传入 MaterialTheme 后，所有 Composable 可通过 MaterialTheme.typography.xxx 访问。
 *
 * 【Material3 预设样式级别（共 15 级）】
 * Display 级（最大）: displayLarge, displayMedium, displaySmall
 * Headline 级（大标题）: headlineLarge, headlineMedium, headlineSmall
 * Title 级（标题）: titleLarge, titleMedium, titleSmall
 * Body 级（正文）: bodyLarge, bodyMedium, bodySmall
 * Label 级（标签）: labelLarge, labelMedium, labelSmall
 *
 * 本项目只覆盖 bodyLarge（默认正文），其余 14 级使用 Material3 内置默认值。
 *
 * 【sp 单位说明】
 * sp (scale-independent pixels) = dp × 用户字体缩放偏好。
 * 与 dp 不同，sp 会响应用户在系统设置中调整的字体大小。
 * Android 推荐所有文本相关尺寸使用 sp 单位以保证无障碍可及性。
 *
 * 【使用示例】
 * Text("标题", style = MaterialTheme.typography.titleLarge)
 * Text("正文", style = MaterialTheme.typography.bodyLarge)
 * Text("标签", style = MaterialTheme.typography.labelSmall)
 */
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,  // 使用系统默认字体（中文通常为思源黑体/Noto Sans）
        fontWeight = FontWeight.Normal,    // 常规字重（非粗体）
        fontSize = 16.sp,                  // 16sp = 默认阅读字号
        lineHeight = 24.sp,                // 行高 = 1.5 倍字号（24/16），舒适的行间距
        letterSpacing = 0.5.sp             // 字间距 0.5sp，略微拉开字符距离，提升中文可读性
    )
)
