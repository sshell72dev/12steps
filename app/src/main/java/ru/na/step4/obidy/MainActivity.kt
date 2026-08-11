package ru.na.step4.obidy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import ru.na.step4.obidy.ui.Step4Nav
import ru.na.step4.obidy.ui.theme.Step4Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Step4Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Step4Nav()
                }
            }
        }
    }
}
