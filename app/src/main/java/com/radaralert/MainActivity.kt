package com.radaralert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.radaralert.ui.navigation.RadarAlertNavHost
import com.radaralert.ui.theme.RadarAlertTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RadarAlertTheme {
                val navController = rememberNavController()
                RadarAlertNavHost(navController = navController)
            }
        }
    }
}
