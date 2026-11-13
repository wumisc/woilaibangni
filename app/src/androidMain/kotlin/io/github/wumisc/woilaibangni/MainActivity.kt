package io.github.wumisc.woilaibangni

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.wumisc.woilaibangni.core.log.LogHub

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 日志初始化放在最前面，且整体兜底：写日志失败绝不能拦住 App 启动
        runCatching { LogHub.init(this) }
        setContent { App() }
    }
}
