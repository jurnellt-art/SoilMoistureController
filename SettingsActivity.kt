package com.anthurium.soilcontroller.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.anthurium.soilcontroller.data.AppPrefs
import com.anthurium.soilcontroller.ui.theme.SoilMoistureTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = AppPrefs(this)

        setContent {
            SoilMoistureTheme {
                SettingsScreen(
                    initialUrl = prefs.getDeviceBaseUrl() ?: "",
                    onSave = { urlInput ->
                        var url = urlInput.trim()
                        if (url.isEmpty()) {
                            Toast.makeText(this, "Please enter a valid address", Toast.LENGTH_SHORT).show()
                        } else {
                            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                url = "http://$url"
                            }
                            prefs.setDeviceBaseUrl(url)
                            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsActivityPreview() {
    SoilMoistureTheme {
        SettingsScreen(
            initialUrl = "http://192.168.1.50",
            onSave = {},
            onBack = {}
        )
    }
}
