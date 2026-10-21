package com.anthurium.soilcontroller.ui.start

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.anthurium.soilcontroller.ui.dashboard.DashboardActivity
import com.anthurium.soilcontroller.ui.theme.SoilMoistureTheme

class StartActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SoilMoistureTheme {
                StartScreen {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                }
            }
        }
    }
}
