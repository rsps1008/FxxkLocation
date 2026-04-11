package com.rsps1008.fxxklocation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rsps1008.fxxklocation.ui.screen.MainScreen
import com.rsps1008.fxxklocation.ui.screen.SettingsScreen
import com.rsps1008.fxxklocation.ui.theme.FxxkLocationTheme
import com.rsps1008.fxxklocation.viewmodel.MainViewModel
import com.rsps1008.fxxklocation.viewmodel.SettingsViewModel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FxxkLocationTheme {
                val navController = rememberNavController()
                
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        val mainViewModel: MainViewModel = viewModel()
                        MainScreen(
                            viewModel = mainViewModel,
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        val settingsViewModel: SettingsViewModel = viewModel()
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
