package com.rhp.aibox

import android.app.Application
import android.content.Context
import com.rhp.aibox.util.PreferenceManager

/**
 * =====================================================================
 * MyApplication - 自定义 Application 类
 * =====================================================================
 *
 * 【Application 类的作用】
 * Application 是 Android 应用的入口点，在应用启动时最先创建，在应用
 * 进程销毁前一直存活。自定义 Application 类常用于：
 * 1. 全局初始化（如数据库、依赖注入、日志框架）
 * 2. 提供全局 Context 引用（通过 companion object）
 * 3. 在 AndroidManifest.xml 中通过 android:name 属性注册
 *
 * 【生命周期】
 * onCreate() → 应用启动时调用一次
 * 不会被 Activity 的生命周期影响，只要应用进程存在就一直存活
 */
class MyApplication : Application() {

    companion object {
        /**
         * 全局 Context 引用
         * 使用 lateinit 延迟初始化，保证在 onCreate() 后可用
         * 注意：不要用这个 Context 来创建 UI 相关对象（会使用错误主题），
         *      仅用于获取系统服务、访问文件、SharedPreferences 等
         */
        lateinit var context: Context
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext

        // ----- 初始化设置管理器 -----
        // PreferenceManager 依赖 Application Context 来访问 SharedPreferences
        // 必须最早初始化，因为其他组件可能在启动时读取设置（如主题模式）
        PreferenceManager.init(applicationContext)
    }
}