package com.radaralert.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.radaralert.ui.screens.MapScreen
import com.radaralert.ui.screens.PermissionScreen
import com.radaralert.ui.screens.SettingsScreen

@Composable
fun RadarAlertNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "permissions"
    ) {
        composable("permissions") {
            PermissionScreen(navController)
        }
        composable("map") {
            MapScreen(navController)
        }
        composable("settings") {
            SettingsScreen(navController)
        }
    }
}
