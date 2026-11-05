package org.pianopath

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import org.pianopath.ui.PianoApp

class MainActivity: ComponentActivity() {
    private lateinit var model: PianoViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model=ViewModelProvider(this)[PianoViewModel::class.java]
        setContent { PianoApp(model) }
    }
    override fun onStart(){super.onStart();if(::model.isInitialized)model.foreground()}
    override fun onStop() {if(::model.isInitialized)model.background();super.onStop()}
}
