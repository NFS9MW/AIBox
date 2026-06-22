package com.rhp.aibox

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rhp.aibox.ui.theme.AIBoxTheme
import com.rhp.aibox.util.PreferenceManager
import com.rhp.aibox.viewmodel.APIActivityViewModel

/**
 * =====================================================================
 * APIActivity - API 配置页面
 * =====================================================================
 *
 * 【功能】
 * 1. 平台选择：支持 DeepSeek / 智谱AI / 阿里云百炼 三个平台
 * 2. API URL：根据选择的平台自动填充，也可手动修改
 * 3. API Key：密码框输入，支持显示/隐藏切换
 * 4. 模型选择：对话框展示预设模型列表 + 自定义输入
 * 5. 获取模型列表：调用平台 API 获取可用模型列表
 *
 * 【数据持久化】
 * 所有配置通过 PreferenceManager → SharedPreferences 存储。
 * 状态由 APIActivityViewModel 集中管理（MVVM 模式）。
 */
class APIActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by remember { mutableIntStateOf(PreferenceManager.getThemeMode()) }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        themeMode = PreferenceManager.getThemeMode()
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }

            AIBoxTheme(themeMode = themeMode) {
                APISettingsScreen(
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}

/**
 * =====================================================================
 * APISettingsScreen - API 配置 UI
 * =====================================================================
 *
 * 【状态管理】
 * 所有 UI 状态由 APIActivityViewModel 管理。
 * 初始值从 PreferenceManager（SharedPreferences）读取。
 * 只有在点击"保存"时才会写入 SharedPreferences。
 *
 * ViewModel 使用 viewModel() 获取，与当前 Activity 生命周期绑定。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun APISettingsScreen(onNavigateBack: () -> Unit) {
    val vm: APIActivityViewModel = viewModel()
    val context = LocalContext.current

    // ----- 状态栏/导航栏图标颜色适配深色模式 -----
    val statusBarView = LocalView.current
    if (!statusBarView.isInEditMode) {
        val darkTheme = when (PreferenceManager.getThemeMode()) {
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

    // 获取当前平台的预设配置（用于模型选择对话框）
    val platformConfig = PreferenceManager.platforms[vm.selectedPlatform]

    // 监听 fetchModelsFromAPI 的结果消息，弹出 Toast 后清除
    LaunchedEffect(vm.fetchResultMessage) {
        vm.fetchResultMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            vm.fetchResultMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API 设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========== 平台选择 ==========
            Text("选择平台", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            // FlowRow 会自动换行：芯片超出屏幕宽度时折行显示（比 Row 更灵活）
            // Row 不会换行，超出部分会被裁剪，因此芯片组使用 FlowRow
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreferenceManager.platforms.forEach { (key, config) ->
                    FilterChip(
                        selected = vm.selectedPlatform == key,
                        onClick = {
                            vm.onPlatformChange(key)  // 切换平台时自动保存旧平台配置、加载新平台配置
                        },
                        label = { Text(config.displayName) }
                    )
                }
            }

            // ========== API URL 输入 ==========
            Text("API URL", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = vm.apiUrl,
                onValueChange = { vm.apiUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("输入 API 地址") }
            )

            // ========== API Key 输入（密码框） ==========
            Text("API Key", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = vm.apiKey,
                onValueChange = { vm.apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("输入 API Key") },
                // 密码可见性切换：PasswordVisualTransformation 将字符显示为圆点
                visualTransformation = if (vm.passwordVisible)
                    VisualTransformation.None      // 明文显示
                else
                    PasswordVisualTransformation(),  // 显示为 ●●●
                trailingIcon = {
                    // 眼睛图标切换密码可见性
                    IconButton(onClick = { vm.passwordVisible = !vm.passwordVisible }) {
                        Icon(
                            if (vm.passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (vm.passwordVisible) "隐藏" else "显示"
                        )
                    }
                }
            )

            // ========== 模型选择 ==========
            Text("模型", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            // 当前选中的模型 + 点击更换的按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = vm.modelName,
                    onValueChange = { vm.modelName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("模型名称") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = { vm.showModelDialog = true }) {
                    Text("选择")
                }
            }

            // ========== 从 API 获取模型列表按钮 ==========
            OutlinedButton(
                onClick = { vm.fetchModelsFromAPI() },
                enabled = !vm.isFetchingModels && vm.apiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (vm.isFetchingModels) "获取中..." else "获取模型列表（从 API）")
            }

            // 显示从 API 获取的模型列表
            if (vm.fetchedModels.isNotEmpty()) {
                Text("API 返回的模型:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.fetchedModels.forEach { model ->
                        FilterChip(
                            selected = vm.modelName == model,
                            onClick = { vm.modelName = model },
                            label = { Text(model) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ========== 保存按钮 ==========
            Button(
                onClick = {
                    vm.save()
                    Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }

    // ========== 模型选择对话框 ==========
    if (vm.showModelDialog) {
        ModelSelectionDialog(
            presetModels = platformConfig?.presetModels ?: emptyList(),
            currentModel = vm.modelName,
            customInput = vm.customModelInput,
            onCustomInputChange = { vm.customModelInput = it },
            onSelect = { selectedModel ->
                vm.modelName = selectedModel
                vm.showModelDialog = false
                vm.customModelInput = ""
            },
            onDismiss = {
                vm.showModelDialog = false
                vm.customModelInput = ""
            }
        )
    }
}

/**
 * =====================================================================
 * ModelSelectionDialog - 模型选择对话框
 * =====================================================================
 *
 * 显示预设模型列表 + 自定义输入区域。
 * 用户点击预设模型直接选中，也可输入自定义模型名并确认。
 *
 * 【AlertDialog 用法】
 * AlertDialog 是 Material3 的标准对话框组件：
 * - title: 对话框标题
 * - text: 对话框主体内容
 * - confirmButton: 确认按钮
 * - dismissButton: 关闭按钮
 */
@Composable
private fun ModelSelectionDialog(
    presetModels: List<String>,
    currentModel: String,
    customInput: String,
    onCustomInputChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择模型") },
        text = {
            Column {
                // 预设模型列表
                presetModels.forEach { model ->
                    val isSelected = model == currentModel
                    Text(
                        text = model,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(model) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary    // 当前选中高亮
                        else
                            MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 自定义模型输入
                Text("自定义模型:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = customInput,
                    onValueChange = onCustomInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("输入模型名称") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 如果有自定义输入，使用自定义值
                    if (customInput.isNotBlank()) {
                        onSelect(customInput.trim())
                    }
                },
                enabled = customInput.isNotBlank()
            ) {
                Text("确认自定义")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
