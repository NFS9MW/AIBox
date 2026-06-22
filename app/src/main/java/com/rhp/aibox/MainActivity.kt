package com.rhp.aibox

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rhp.aibox.model.Role
import com.rhp.aibox.ui.theme.AIBoxTheme
import com.rhp.aibox.util.PreferenceManager
import com.rhp.aibox.viewmodel.ChatMessage
import com.rhp.aibox.viewmodel.Conversation
import com.rhp.aibox.viewmodel.MainActivityViewModel
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch

/**
 * =====================================================================
 * MainActivity - 应用主界面（唯一的主 Activity）
 * =====================================================================
 *
 * 【界面结构】
 * ModalNavigationDrawer（侧边抽屉）
 *   └── Scaffold
 *        ├── TopAppBar（标题 + 菜单按钮 + 设置按钮）
 *        └── Column
 *             ├── ChatMessageList（对话气泡列表，LazyColumn）
 *             └── ChatInputBar（输入框 + 发送按钮）
 *
 * 【主题设置】
 * 使用 Compose State + LifecycleObserver 管理主题模式：
 * - 初始值从 PreferenceManager 读取
 * - 在 onResume 时重新读取（确保从设置页返回后主题生效）
 * - themeMode 是 Compose State，变化时自动重组 AIBoxTheme
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 使用 Compose State 存储主题模式，变化时自动触发 UI 重组
            var themeMode by remember { mutableIntStateOf(PreferenceManager.getThemeMode()) }

            // 在 ON_RESUME 时重新读取主题设置
            // 用于：从 SettingsActivity 返回后，应用用户对主题的更改
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
                ChatScreen()
            }
        }
    }
}

/**
 * =====================================================================
 * ChatScreen - 主聊天界面 Composable
 * =====================================================================
 *
 * 【状态来源】
 * 所有聊天数据由 MainActivityViewModel 管理，通过 vm 参数传入。
 * viewModel() 是 lifecycle-viewmodel-compose 提供的 Composable 函数，
 * 用于获取与当前 Activity/Fragment 生命周期绑定的 ViewModel 实例。
 *
 * 【抽屉导航】
 * ModalNavigationDrawer 是 Material3 的侧边抽屉组件：
 * - drawerState: 控制抽屉的开/关状态
 * - drawerContent: 抽屉内显示的内容（对话历史列表）
 * - content: 主内容区（Scaffold + 聊天界面）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: MainActivityViewModel = viewModel()) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ----- 状态栏/导航栏图标颜色适配深色模式 -----
    // SideEffect 在每次成功重组后同步执行，确保状态栏颜色与当前主题一致
    // WindowCompat.getInsetsController() 是 AndroidX 提供的兼容 API，
    // 可在 API 23+ 上控制状态栏图标颜色（isAppearanceLightStatusBars = 亮色图标）
    val view = LocalView.current
    if (!view.isInEditMode) {  // Android Studio 预览模式下跳过，避免预览崩溃
        val themeMode = PreferenceManager.getThemeMode()
        val darkTheme = when (themeMode) {
            1 -> false
            2 -> true
            else -> isSystemInDarkTheme()
        }
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // 抽屉打开时，返回键关闭抽屉而非退出应用
    // BackHandler 拦截系统返回手势 / 返回键，enabled 仅在抽屉打开时为 true
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // 从 ViewModel 读取当前状态
    val conversations = vm.conversations
    val currentIndex = vm.currentConversationIndex
    val currentMessages = vm.currentMessages
    val isStreaming = vm.isStreaming
    val title = vm.currentConversation?.title ?: "AIBox"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ChatDrawerContent(
                    conversations = conversations,
                    currentIndex = currentIndex,
                    onNewConversation = {
                        vm.createNewConversation()
                        scope.launch { drawerState.close() }
                    },
                    onSelectConversation = { index ->
                        vm.selectConversation(index)
                        scope.launch { drawerState.close() }
                    },
                    onDeleteConversation = { index ->
                        vm.deleteConversation(index)
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    // 左侧：侧边栏菜单按钮
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "历史对话")
                        }
                    },
                    // 右侧：设置按钮
                    actions = {
                        IconButton(onClick = {
                            // 使用 Intent 启动 SettingsActivity
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                // 消息列表
                if (currentMessages.isEmpty()) {
                    EmptyChatPlaceholder(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                } else {
                    ChatMessageList(
                        messages = currentMessages,
                        isStreaming = isStreaming,
                        scrollToBottomTrigger = vm.scrollToBottomTrigger,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }

                // 底部输入栏
                //HorizontalDivider()
                ChatInputBar(
                    inputText = vm.inputText,
                    onInputChange = { vm.inputText = it },
                    onSend = { vm.sendMessage() },
                    onStop = { vm.stopMessage() },
                    enabled = vm.inputText.isNotBlank(),
                    isStreaming = isStreaming
                )
            }
        }
    }
}

/**
 * =====================================================================
 * ChatDrawerContent - 侧边栏对话历史列表
 * =====================================================================
 *
 * 【布局】
 * - 顶部标题行："对话历史" + 新建按钮（+号）
 * - LazyColumn：全部对话的列表，支持点击切换和删除
 *
 * 【选中高亮】
 * 当前活跃的对话使用 primaryContainer 背景色高亮显示，标题加粗。
 */
@Composable
private fun ChatDrawerContent(
    conversations: List<Conversation>,
    currentIndex: Int,
    onNewConversation: () -> Unit,
    onSelectConversation: (Int) -> Unit,
    onDeleteConversation: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "对话历史",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onNewConversation) {
                Icon(Icons.Default.Add, contentDescription = "新建对话")
            }
        }

        HorizontalDivider()

        // 对话列表
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(conversations) { index, conversation ->
                val isSelected = index == currentIndex
                ConversationItem(
                    conversation = conversation,
                    isSelected = isSelected,
                    onClick = { onSelectConversation(index) },
                    onDelete = { onDeleteConversation(index) }
                )
            }
        }
    }
}

/**
 * ConversationItem - 侧边栏中的单个对话条目
 *
 * 显示对话标题（第一条用户消息的前20字符）和内容预览（前40字符）。
 * 当前活跃对话使用 primaryContainer 背景色高亮。
 * 右侧删除按钮可移除该对话。
 *
 * @param conversation 对话对象
 * @param isSelected   是否为当前活跃对话
 * @param onClick      点击切换到该对话
 * @param onDelete     删除该对话的回调
 */
@Composable
private fun ConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val preview = conversation.messages.firstOrNull()?.content?.take(40) ?: "空对话"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
                if (preview.isNotEmpty()) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除对话",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * =====================================================================
 * EmptyChatPlaceholder - 空对话欢迎界面
 * =====================================================================
 *
 * 当当前对话没有消息时显示，提示用户开始输入。
 * 居中显示应用名称和引导文字。
 */
@Composable
private fun EmptyChatPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "AIBox",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "在下方输入消息开始对话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * =====================================================================
 * ChatMessageList - 消息列表（LazyColumn 实现）
 * =====================================================================
 *
 * 【性能优化】
 * - LazyColumn: 只渲染可见区域的消息，适合长对话列表
 * - key = msg.id: 稳定的 key 帮助 Compose 正确识别每个 item
 *
 * 【自动滚动策略】
 * - 默认跟随：autoScroll=true 时用非动画 scrollToItem 追底
 * - 用户上滑离开底部 → autoScroll=false，保持阅读位置不动
 * - 用户滑回底部 → autoScroll=true，恢复自动跟随
 * - 非动画 scrollToItem 消除逐 token 动画冲突导致的"抽动"
 */
@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    scrollToBottomTrigger: Long,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }

    LaunchedEffect(scrollToBottomTrigger) {
        if (messages.isEmpty()) return@LaunchedEffect

        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        val atBottom = lastVisible == null || lastVisible >= messages.lastIndex - 1

        if (!autoScroll && atBottom && !listState.isScrollInProgress) {
            // 用户滑回了底部 → 恢复自动跟随
            autoScroll = true
        }

        if (autoScroll && atBottom) {
            // 非动画滚动：避免 animateScrollToItem 逐 token 动画冲突导致气泡抽动
            listState.scrollToItem(messages.lastIndex, Int.MAX_VALUE)
        }

        if (!atBottom && !listState.isScrollInProgress) {
            // 用户手动上滑离开底部 → 暂停自动跟随，保持阅读位置
            autoScroll = false
        }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(messages, key = { _, msg -> msg.id }) { index, message ->
            ChatBubble(
                message = message,
                isStreaming = isStreaming && index == messages.lastIndex && message.role == Role.ANSWER
            )
        }
    }
}

/**
 * =====================================================================
 * ChatBubble - 单条消息气泡组件
 * =====================================================================
 *
 * 【角色区分】
 * - USER（用户）: 右对齐，primary 背景色，白色文字
 * - ANSWER（AI回答）: 左对齐，surfaceVariant 背景，MarkdownText 渲染
 * - REASONING（AI推理）: 左对齐，半透明背景，斜体小字
 *
 * 【弹性宽度】
 * Column 使用 fillMaxWidth(0.85f) 实现响应式宽度：
 * 手机（360dp）→ 约 306dp；平板（1024dp）→ 约 870dp，
 * 自动适配不同屏幕尺寸。
 *
 * 【气泡形状】
 * 使用 RoundedCornerShape 实现"小尾巴"效果：
 * - 用户气泡右下角直角（4dp），其余圆角（16dp）
 * - AI 气泡左下角直角（4dp），其余圆角（16dp）
 */
@Composable
private fun ChatBubble(message: ChatMessage, isStreaming: Boolean) {
    val isUser = message.role == Role.USER

    // 外层 Box 撑满宽度，contentAlignment 将气泡推向左侧或右侧
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.85f),  // 弹性宽度：手机 ~300dp，平板 ~700dp+
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
        // 角色标签
        if (!isUser) {
            Text(
                text = when (message.role) {
                    Role.REASONING -> "深度思考"
                    Role.ANSWER -> "AI"
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }

        val clipboardManager = LocalClipboardManager.current

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = when {
                isUser -> MaterialTheme.colorScheme.primary
                message.role == Role.REASONING -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            when {
                isUser -> {
                    // 覆盖选中色：primary 背景上使用 onPrimary 半透明，确保可见
                    CompositionLocalProvider(
                        LocalTextSelectionColors provides TextSelectionColors(
                            handleColor = MaterialTheme.colorScheme.onPrimary,
                            backgroundColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                        )
                    ) {
                        SelectionContainer {
                            Text(
                                text = message.content,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                message.role == Role.REASONING -> {
                    SelectionContainer {
                        Text(
                            text = message.content,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                message.role == Role.ANSWER && message.content.isNotEmpty() -> {
                    Column {
                        MarkdownText(
                            markdown = message.content,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)
                        )
                        Text(
                            text = "复制",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(end = 12.dp, bottom = 8.dp)
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(message.content))
                                }
                        )
                    }
                }

                message.role == Role.ANSWER && isStreaming -> {
                    // 流式输出中，等待第一个 token
                    Box(modifier = Modifier.padding(12.dp)) {
                        TypingIndicator()
                    }
                }
            }
        }
        }  // 闭合 Column
    }  // 闭合 Box
}

/**
 * TypingIndicator - AI 正在思考/输入中的动画指示器
 *
 * 三个小圆点并排显示，模拟"对方正在输入..."的视觉效果。
 * 当 AI 回复流尚未返回第一个 token 时显示（isStreaming = true 且 content 为空）。
 *
 * 注意：无实际动画，三个圆点始终静态显示。
 * 如需跳动动画，可配合 animateFloatAsState / Animatable 实现。
 */
@Composable
private fun TypingIndicator() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
            )
        }
    }
}

/**
 * ChatInputBar - 底部输入栏
 *
 * 包含多行输入框（OutlinedTextField）+ 发送/停止按钮。
 * RoundedCornerShape(24.dp) 大圆角适配现代手机屏幕，
 * bottom = 16.dp 为全面屏圆角区域预留空间。
 *
 * 流式输出期间输入框禁用编辑，发送按钮切换为红色停止按钮。
 */
@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    enabled: Boolean,
    isStreaming: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            placeholder = { Text("输入消息...") },
            maxLines = 4,
            enabled = !isStreaming  // 流式输出时禁用输入框编辑
        )

        if (isStreaming) {
            // 流式输出中 → 显示停止按钮
            IconButton(
                onClick = onStop,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "停止生成",
                    tint = MaterialTheme.colorScheme.error  // 红色以示"终止"操作
                )
            }
        } else {
            // 正常状态 → 显示发送按钮
            IconButton(
                onClick = onSend,
                enabled = enabled,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = if (enabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}
