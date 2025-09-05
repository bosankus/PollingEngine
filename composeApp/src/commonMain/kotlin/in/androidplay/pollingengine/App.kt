package `in`.androidplay.pollingengine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.rememberCoroutineScope
import `in`.androidplay.pollingengine.sample.PollingSamples.demoPoll
import kotlinx.coroutines.launch

import pollingengine.composeapp.generated.resources.Res
import pollingengine.composeapp.generated.resources.compose_multiplatform

@Composable
@Preview
fun App() {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        var demoResult by remember { mutableStateOf<String?>(null) }
        var isRunning by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = { showContent = !showContent }) {
                Text("Toggle Greeting")
            }
            AnimatedVisibility(showContent) {
                val greeting = remember { Greeting().greet() }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(Res.drawable.compose_multiplatform), null)
                    Text("Compose: $greeting")
                }
            }
            Button(onClick = {
                if (!isRunning) {
                    isRunning = true
                    demoResult = null
                    scope.launch {
                        demoResult = demoPoll()
                        isRunning = false
                    }
                }
            }) {
                Text(if (isRunning) "Running…" else "Run Polling Demo")
            }
            val resultText = when {
                isRunning -> "Polling in progress..."
                demoResult != null -> "Result: ${demoResult}"
                else -> ""
            }
            if (resultText.isNotEmpty()) {
                Text(resultText)
            }
        }
    }
}