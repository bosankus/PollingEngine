package `in`.androidplay.pollingengine

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

// Exposed to Swift as `MainViewControllerKt.MainViewController()`
@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
