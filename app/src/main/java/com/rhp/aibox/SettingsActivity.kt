package com.rhp.aibox

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rhp.aibox.ui.theme.AIBoxTheme
import com.rhp.aibox.util.PreferenceManager
import com.rhp.aibox.viewmodel.SettingsActivityViewModel

/**
 * =====================================================================
 * SettingsActivity - 应用设置页面
 * =====================================================================
 *
 * 【功能】
 * 提供三个设置项，每个用 OutlinedCard 包裹：
 * 1. 深色模式：三个 FilterChip（跟随系统 / 浅色 / 深色）
 * 2. API 设置：点击跳转到 APIActivity 配置平台和密钥
 * 3. 系统上下文：点击跳转到 ContextActivity 编辑 System Prompt
 *
 * 【导航方式】
 * 使用传统 Intent 跳转，非 Navigation Compose。
 * 原因：设置页面是一个独立的 Activity，用 Intent 最简单直接。
 *
 * 【状态管理】
 * 深色模式的状态由 SettingsActivityViewModel 管理（MVVM 模式），
 * API 设置和系统上下文卡片直接读取 PreferenceManager 的最新值展示。
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 从 ViewModel 读取主题设置（Compose State，变化时自动触发重组）
            val vm: SettingsActivityViewModel = viewModel()
            AIBoxTheme(themeMode = vm.themeMode) {
                SettingsScreen(
                    vm = vm,
                    onNavigateBack = { finish() },  // 返回键 → 关闭当前 Activity
                    onNavigateToAPI = {
                        // 跳转到 API 设置页面
                        startActivity(Intent(this, APIActivity::class.java))
                    },
                    onNavigateToContext = {
                        // 跳转到系统上下文编辑页面
                        startActivity(Intent(this, ContextActivity::class.java))
                    }
                )
            }
        }
    }
}

/**
 * =====================================================================
 * SettingsScreen - 设置页面 UI
 * =====================================================================
 *
 * 【布局结构】
 * Scaffold (TopAppBar + Body)
 *   └── Column (verticalScroll)
 *        ├── OutlinedCard: 深色模式（3个 FilterChip，状态由 ViewModel 管理）
 *        ├── OutlinedCard: API 设置（可点击跳转）
 *        └── OutlinedCard: 系统上下文（可点击跳转）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    vm: SettingsActivityViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAPI: () -> Unit,
    onNavigateToContext: () -> Unit
) {
    // ----- 状态栏/导航栏图标颜色适配深色模式 -----
    val statusBarView = LocalView.current
    if (!statusBarView.isInEditMode) {
        val darkTheme = when (vm.themeMode) {
            1 -> false
            2 -> true
            else -> isSystemInDarkTheme()
        }
        SideEffect {
            val window = (statusBarView.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, statusBarView)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // 从子页面返回时刷新卡片显示的最新配置值
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onResume()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    // Material3 规范的返回箭头
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())  // 允许内容滚动
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)  // 卡片间距
        ) {
            // ========== 1. 深色模式设置 ==========
            DarkModeCard(
                selectedMode = vm.themeMode,
                onModeSelected = { vm.setThemeMode(it) }
            )

            // ========== 2. API 设置 ==========
            APISettingsCard(
                subtitle = vm.apiCardSubtitle,
                onClick = onNavigateToAPI
            )

            // ========== 3. 系统上下文设置 ==========
            SystemContextCard(
                preview = vm.contextCardPreview,
                onClick = onNavigateToContext
            )
        }
    }
}

// =====================================================================
// 深色模式卡片
// =====================================================================

/**
 * DarkModeCard - 深色模式选择器
 *
 * 使用 FilterChip（Material3 的选择芯片组件）提供三种模式：
 * - 跟随系统（默认）：根据 Android 系统的深色模式设置自动切换
 * - 浅色模式：始终使用亮色主题
 * - 深色模式：始终使用暗色主题
 *
 * 状态由 ViewModel 管理，选择后通过回调通知 ViewModel 更新并持久化。
 *
 * @param selectedMode 当前选中的模式 (0/1/2)
 * @param onModeSelected 选中新模式时的回调
 */
@Composable
private fun DarkModeCard(selectedMode: Int, onModeSelected: (Int) -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        // OutlinedCard 的默认样式：带边框的卡片
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 卡片标题
            Text(
                text = "深色模式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "选择应用的主题外观",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // FilterChip 行
            // Arrangement.spacedBy 为子元素之间添加固定间距
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 跟随系统 (mode = 0)
                FilterChip(
                    selected = selectedMode == 0,
                    onClick = { onModeSelected(0) },
                    label = { Text("跟随系统") }
                )
                // 浅色模式 (mode = 1)
                FilterChip(
                    selected = selectedMode == 1,
                    onClick = { onModeSelected(1) },
                    label = { Text("浅色") }
                )
                // 深色模式 (mode = 2)
                FilterChip(
                    selected = selectedMode == 2,
                    onClick = { onModeSelected(2) },
                    label = { Text("深色") }
                )
            }
        }
    }
}

// =====================================================================
// API 设置卡片
// =====================================================================

/**
 * APISettingsCard - API 配置入口
 *
 * 显示当前平台和模型名称，点击跳转到 APIActivity 配置。
 * 副标题由 ViewModel 提供（Compose State），
 * 从子页面返回时 ViewModel.onResume() 刷新，确保显示最新值。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun APISettingsCard(subtitle: String, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),  // 整卡可点击
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "API 设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 显示当前平台和模型（由 ViewModel 提供，子页面返回后自动刷新）
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 右箭头指示可点击（Unicode 单右书名号，比 ">" 更美观）
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =====================================================================
// 系统上下文卡片
// =====================================================================

/**
 * SystemContextCard - 系统上下文入口
 *
 * 显示系统提示词预览（前 50 字符），点击跳转到 ContextActivity 编辑。
 * 预览文本由 ViewModel 提供，从子页面返回时自动刷新。
 * 系统上下文（System Prompt）是 LLM 对话的第一条特殊消息，
 * 用于定义 AI 的角色、行为准则和回答风格。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SystemContextCard(preview: String, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "系统上下文",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1  // 只显示一行预览
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
