package com.rhp.aibox

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.rhp.aibox.viewmodel.ContextActivityViewModel

/**
 * =====================================================================
 * ContextActivity - 系统上下文编辑页面
 * =====================================================================
 *
 * 【什么是系统上下文（System Prompt）】
 * 系统上下文是发送给 LLM 的第一条特殊消息（role = "system"），
 * 用于定义 AI 的行为准则、角色设定、知识范围、回答格式等。
 * 例如：
 * - "你是一个专业的编程助手，请用中文回答。"
 * - "你是一个客服机器人，语气友好，不要透露内部信息。"
 *
 * 【功能】
 * 1. 多行文本编辑器：支持输入较长的系统提示词
 * 2. 文件导入：从手机存储中导入 .txt/.md 文件作为系统上下文
 * 3. 保存：写入 SharedPreferences 持久化
 *
 * 【文件导入原理】
 * 使用 Android 的存储访问框架（SAF - Storage Access Framework）：
 * 1. ActivityResultContracts.OpenDocument 启动系统文件选择器
 * 2. 用户选择文件后返回 content:// URI
 * 3. 通过 ContentResolver.openInputStream() 读取文件内容
 * 4. 将内容填充到文本编辑框中
 *
 * 【状态管理】
 * 文本状态由 ContextActivityViewModel 管理，
 * 文件选择器由 Compose 层管理（需 Composable 生命周期）。
 */
class ContextActivity : ComponentActivity() {
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
                ContextScreen(
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}

/**
 * =====================================================================
 * ContextScreen - 系统上下文编辑 UI
 * =====================================================================
 *
 * 【布局】
 * Scaffold (TopAppBar + Body)
 *   └── Column
 *        ├── 说明文字
 *        ├── OutlinedTextField（多行，固定 400dp 高度）
 *        └── Row
 *             ├── OutlinedButton（从文件导入）
 *             └── Button（保存）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextScreen(onNavigateBack: () -> Unit) {
    val vm: ContextActivityViewModel = viewModel()
    val androidContext = LocalContext.current

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

    // ========== 文件选择器启动器（SAF - Storage Access Framework） ==========
    // rememberLauncherForActivityResult 在 Composable 生命周期内注册 Activity 结果回调
    // ActivityResultContracts.OpenDocument() 调用系统文件选择器，返回用户选择的文件 content:// URI
    // 回调 lambda 在用户选择文件后执行（uri 非空 = 选择了文件，null = 取消操作）
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { vm.readTextFromUri(it) }?.let { content ->
            vm.contextText = content  // 将文件内容填入编辑框
            Toast.makeText(androidContext, "已导入文件内容", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("系统上下文") },
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
            // 说明文字
            Text(
                text = "系统上下文（System Prompt）定义 AI 的行为角色。" +
                        "它会在每次对话请求中作为第一条指令发送给模型。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ========== 多行文本编辑器 ==========
            OutlinedTextField(
                value = vm.contextText,
                onValueChange = { vm.contextText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),  // 固定高度，便于编辑长文本
                placeholder = { Text("输入系统提示词...") },
                // 不限制最大行数，允许用户输入任意长度的文本
                maxLines = Int.MAX_VALUE
            )

            // ========== 操作按钮行 ==========
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 从文件导入按钮
                OutlinedButton(
                    onClick = {
                        // 启动文件选择器，只显示文本文件
                        filePickerLauncher.launch(arrayOf(
                            "text/plain",        // .txt 文件
                            "text/markdown",     // .md 文件
                            "text/*"             // 其他文本文件
                        ))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("从文件导入")
                }

                // 保存按钮
                Button(
                    onClick = {
                        vm.save()
                        Toast.makeText(androidContext, "已保存", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
            }

            // 底部留白，防止键盘遮挡
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
