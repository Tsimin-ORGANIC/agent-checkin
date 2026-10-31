package com.agentcheckin.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentcheckin.android.ui.AgentCheckinTheme
import com.agentcheckin.android.ui.AppRoot
import com.agentcheckin.android.ui.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgentCheckinTheme {
                val viewModel: MainViewModel = viewModel()
                AppRoot(viewModel)
            }
        }
    }
}
