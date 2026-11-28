package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.RaabtaAppRoot
import com.example.ui.RaabtaViewModel
import com.example.ui.theme.RaabtaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light status and navigation bar icons on the black background
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            RaabtaTheme {
                val raabtaViewModel: RaabtaViewModel = viewModel()
                RaabtaAppRoot(viewModel = raabtaViewModel)
            }
        }
    }
}
