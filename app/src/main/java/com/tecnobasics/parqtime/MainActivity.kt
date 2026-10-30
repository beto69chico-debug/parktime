package com.tecnobasics.parqtime

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.tecnobasics.parqtime.ui.ParqApp
import com.tecnobasics.parqtime.ui.ParqTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_ADD = "open_add"
    }

    private val openAdd = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifs.ensureChannels(this)
        SessionController.refresh(this) // re-sincroniza alarmas por si el sistema las borró
        openAdd.value = intent?.getBooleanExtra(EXTRA_OPEN_ADD, false) == true
        setContent {
            ParqTheme {
                ParqApp(openAdd = openAdd.value, onOpenAddHandled = { openAdd.value = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_ADD, false)) openAdd.value = true
    }
}
