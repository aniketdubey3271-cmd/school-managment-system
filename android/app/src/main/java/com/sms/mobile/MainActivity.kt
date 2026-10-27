package com.sms.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sms.mobile.ui.SmsRoot
import com.sms.mobile.ui.theme.SmsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val client = (application as SmsApplication).client
        setContent {
            SmsTheme {
                SmsRoot(client)
            }
        }
    }
}
