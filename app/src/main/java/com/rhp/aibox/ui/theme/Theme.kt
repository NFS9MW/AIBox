package com.rhp.aibox.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.rhp.aibox.util.PreferenceManager

/**
 * =====================================================================
 * 暗色主题配色方案
 * =====================================================================
 *
 * Material3 的 darkColorScheme 定义了一套适合暗色背景的颜色。
 * - primary: 主要品牌色，用于按钮、选中状态等
 * - secondary: 次要品牌色，用于强调元素
 * - tertiary: 第三品牌色，用于对比元素
 */
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

/**
 * 亮色主题配色方案
 */
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/**
 * =====================================================================
 * AIBoxTheme - 应用主题包装器
 * =====================================================================
 *
 * 【参数说明】
 * @param darkTheme   是否使用暗色主题，默认跟随系统设置
 * @param dynamicColor 是否使用 Android 12+ 的动态取色（Monet），默认开启
 * @param themeMode   强制主题模式：0=跟随系统, 1=强制浅色, 2=强制深色
 *                    从 PreferenceManager 读取，用于用户手动设置主题
 * @param content     需要被主题包裹的 Composable 内容
 *
 * 【颜色决策逻辑】
 * 1. 如果用户在设置中选择了"强制浅色"（themeMode == 1）→ darkTheme = false
 * 2. 如果用户在设置中选择了"强制深色"（themeMode == 2）→ darkTheme = true
 * 3. 如果用户选择"跟随系统"（themeMode == 0）→ darkTheme 由系统决定
 * 4. 如果 darkTheme == true 且支持动态取色 → 使用 dynamicDarkColorScheme
 * 5. 否则使用自定义配色方案
 */
@Composable
fun AIBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeMode: Int = 0,  // 从外部传入设置中的主题模式
    content: @Composable () -> Unit
) {
    // 根据用户的主题设置覆盖 darkTheme
    val effectiveDarkTheme = when (themeMode) {
        1 -> false   // 强制浅色模式
        2 -> true    // 强制深色模式
        else -> darkTheme  // 0 或默认：跟随系统
    }

    val colorScheme = when {
        // Android 12+ 支持动态取色（Monet 主题引擎）
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (effectiveDarkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }

        effectiveDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
