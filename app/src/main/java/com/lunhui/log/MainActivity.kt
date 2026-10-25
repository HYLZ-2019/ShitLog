package com.lunhui.log

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lunhui.log.ui.LogApp
import com.lunhui.log.ui.theme.LunhuiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: LogViewModel = viewModel()
            val theme by model.theme.collectAsStateWithLifecycle()
            LunhuiTheme(theme) { LogApp(model) }
        }
    }
}
