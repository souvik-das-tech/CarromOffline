package com.bbcl.carromoffline

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme


class MainActivity : ComponentActivity() {
    private val model: GameModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The game always uses its own dark walnut look, so system bars get light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // A game can sit on the table between turns; don't let the screen go dark.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Night, surface = Night)) {
                val screen = when {
                    model.game != null -> 2
                    model.lobbyOpen -> 1
                    else -> 0
                }
                AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") {
                    when (it) {
                        2 -> GameScreen(model)
                        1 -> LobbyScreen(model)
                        else -> SetupScreen(model)
                    }
                }
            }
        }
    }
}
