package com.volkan.c2mblocker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.volkan.c2mblocker.ui.C2MApp
import com.volkan.c2mblocker.ui.C2MTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Şifre ekran görüntüsüne ve son uygulamalar önizlemesine düşmesin.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            C2MTheme {
                C2MApp()
            }
        }
    }
}
