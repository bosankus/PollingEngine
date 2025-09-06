package `in`.androidplay.pollingengine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.androidplay.pollingengine.models.PollingResult
import `in`.androidplay.pollingengine.polling.BackoffPolicy
import `in`.androidplay.pollingengine.polling.PollingEngine
import `in`.androidplay.pollingengine.polling.PollingOutcome
import `in`.androidplay.pollingengine.polling.pollingConfig
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.pow

// ------- Models and helpers (moved above App to avoid any potential local-declaration parsing issues) -------


@Composable
private fun TerminalLog(modifier: Modifier = Modifier, logs: List<String>) {
    val bg = Color(0xFF0F1115)
    val border = Color(0xFF2A2F3A)

    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex.coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)
            ) {
                items(items = logs, key = { it.hashCode() }) { line ->
                    androidx.compose.animation.AnimatedVisibility(visible = true) {
                        LogEntryCard(line = line)
                    }
                }
            }
        }
    }
}

// --- Simple log entry card without icons for multiplatform compatibility ---
@Composable
private fun LogEntryCard(line: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when {
        line.startsWith("[error]") -> Color(0x22FF5A5A) to Color(0xFFFFB4B4)
        line.startsWith("[done]") -> Color(0x2220E5A8) to Color(0xFFB2FFE5)
        line.startsWith("[info]") -> Color(0x222A7FFF) to Color(0xFFB7D8FF)
        else -> Color(0x222A2F3A) to Color(0xFFE5E7EB)
    }
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        color = bgColor,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp)
        ) {
            Text(
                text = line,
                color = textColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PropertiesCard(
    initialDelayText: String, onInitialChange: (String) -> Unit,
    maxDelayText: String, onMaxDelayChange: (String) -> Unit,
    multiplierText: String, onMultiplierChange: (String) -> Unit,
    jitterText: String, onJitterChange: (String) -> Unit,
    maxAttemptsText: String, onMaxAttemptsChange: (String) -> Unit,
    overallTimeoutText: String, onOverallTimeoutChange: (String) -> Unit,
    perAttemptTimeoutText: String, onPerAttemptTimeoutChange: (String) -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Delays & Attempts",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    LabeledField("initialDelayMs", initialDelayText, onInitialChange)
                    LabeledField("maxAttempts", maxAttemptsText, onMaxAttemptsChange)
                    LabeledField(
                        "perAttemptTimeoutMs",
                        perAttemptTimeoutText,
                        onPerAttemptTimeoutChange
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Backoff & Timeouts",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    LabeledField("maxDelayMs", maxDelayText, onMaxDelayChange)
                    LabeledField("multiplier", multiplierText, onMultiplierChange)
                    LabeledField("jitterRatio", jitterText, onJitterChange)
                    LabeledField("overallTimeoutMs", overallTimeoutText, onOverallTimeoutChange)
                }
            }
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun <T> describeResult(result: PollingResult<T>): String = when (result) {
    is PollingResult.Success -> "Success(${result.data})"
    is PollingResult.Failure -> "Failure(code=${result.error.code}, msg=${result.error.message})"
    is PollingResult.Waiting -> "Waiting"
    is PollingResult.Cancelled -> "Cancelled"
    is PollingResult.Unknown -> "Unknown"
}

private fun <T> describeOutcome(outcome: PollingOutcome<T>): String = when (outcome) {
    is PollingOutcome.Success -> {
        val secs = (outcome.elapsedMs / 100L).toFloat() / 10f
        "Success(value=${outcome.value}, attempts=${outcome.attempts}, elapsedSec=${
            ((kotlin.math.round(
                secs * 10f
            )) / 10f)
        })"
    }

    is PollingOutcome.Exhausted -> {
        val secs = (outcome.elapsedMs / 100L).toFloat() / 10f
        "Exhausted(attempts=${outcome.attempts}, elapsedSec=${((kotlin.math.round(secs * 10f)) / 10f)})"
    }

    is PollingOutcome.Timeout -> {
        val secs = (outcome.elapsedMs / 100L).toFloat() / 10f
        "Timeout(attempts=${outcome.attempts}, elapsedSec=${((kotlin.math.round(secs * 10f)) / 10f)})"
    }

    is PollingOutcome.Cancelled -> {
        val secs = (outcome.elapsedMs / 100L).toFloat() / 10f
        "Cancelled(attempts=${outcome.attempts}, elapsedSec=${((kotlin.math.round(secs * 10f)) / 10f)})"
    }
}

@Composable
private fun GlowingButton(
    enabled: Boolean,
    text: String,
    onClick: () -> Unit,
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

private fun buildTechTypography(
    base: androidx.compose.material3.Typography,
    family: FontFamily
): androidx.compose.material3.Typography {
    return androidx.compose.material3.Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

// ------- Main App -------

@Composable
@Preview
fun App() {
    // Global advanced dark theme with custom typography
    val neonPrimary = Color(0xFF00E5A8)
    val bg = Color(0xFF0B1015)
    val onBg = Color(0xFFE6F1FF)
    val darkScheme = androidx.compose.material3.darkColorScheme(
        primary = neonPrimary,
        onPrimary = Color(0xFF00110A),
        background = bg,
        onBackground = onBg,
        surface = Color(0xFF111823),
        onSurface = onBg,
        surfaceVariant = Color(0xFF172232),
        onSurfaceVariant = Color(0xFFB7C4D6),
        outline = Color(0xFF334155),
    )
    val baseTypography = androidx.compose.material3.Typography()
    val techTypography = buildTechTypography(baseTypography, FontFamily.SansSerif)

    MaterialTheme(colorScheme = darkScheme, typography = techTypography) {
        val scope = rememberCoroutineScope()
        var isRunning by remember { mutableStateOf(false) }
        var isPaused by remember { mutableStateOf(false) }
        var handle by remember { mutableStateOf<PollingEngine.Handle?>(null) }
        val logs = remember { mutableStateListOf<String>() }
        var remainingMs by remember { mutableStateOf(0L) }
        var showProperties by remember { mutableStateOf(false) }

        // Editable property state (as text for easy input/validation)
        var initialDelayText by remember { mutableStateOf("500") }
        var maxDelayText by remember { mutableStateOf("5000") }
        var multiplierText by remember { mutableStateOf("1.8") }
        var jitterText by remember { mutableStateOf("0.15") }
        var maxAttemptsText by remember { mutableStateOf("12") }
        var overallTimeoutText by remember { mutableStateOf("30000") }
        var perAttemptTimeoutText by remember { mutableStateOf("") } // empty = null

        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(Modifier.height(40.dp))
                // Heading
                Text(
                    text = "Polling Terminal",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))

                // Start button + countdown
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GlowingButton(
                        enabled = true,
                        text = when {
                            !isRunning -> "Start Polling"
                            isPaused -> "Resume"
                            else -> "Pause"
                        },
                        onClick = {
                            fun appendLog(msg: String) {
                                scope.launch { logs.add(msg) }
                            }
                            if (!isRunning) {
                                logs.clear()

                                // Parse and validate inputs
                                val initialDelay = initialDelayText.toLongOrNull()
                                val maxDelay = maxDelayText.toLongOrNull()
                                val multiplier = multiplierText.toDoubleOrNull()
                                val jitter = jitterText.toDoubleOrNull()
                                val maxAttempts = maxAttemptsText.toIntOrNull()
                                val overallTimeout = overallTimeoutText.toLongOrNull()
                                val perAttemptTimeout =
                                    perAttemptTimeoutText.trim().ifEmpty { null }?.toLongOrNull()

                                if (initialDelay == null || maxDelay == null || multiplier == null || jitter == null || maxAttempts == null || overallTimeout == null || (perAttemptTimeoutText.isNotEmpty() && perAttemptTimeout == null)) {
                                    appendLog("[error] Invalid properties. Please enter valid numbers.")
                                    return@GlowingButton
                                }

                                val backoff = try {
                                    BackoffPolicy(
                                        initialDelayMs = initialDelay,
                                        maxDelayMs = maxDelay,
                                        multiplier = multiplier,
                                        jitterRatio = jitter,
                                        maxAttempts = maxAttempts,
                                        overallTimeoutMs = overallTimeout,
                                        perAttemptTimeoutMs = perAttemptTimeout,
                                    )
                                } catch (t: Throwable) {
                                    appendLog("[error] ${t.message}")
                                    return@GlowingButton
                                }

                                isRunning = true
                                isPaused = false
                                remainingMs = backoff.overallTimeoutMs

                                // Sample finish logic: succeed on the 8th attempt (to show exponential logs)
                                var attemptCounter = 0

                                val config = pollingConfig<String> {
                                    fetch {
                                        attemptCounter++
                                        if (attemptCounter < 8) {
                                            PollingResult.Waiting
                                        } else {
                                            PollingResult.Success("Ready at attempt #$attemptCounter")
                                        }
                                    }
                                    success { value -> value.isNotEmpty() }
                                    backoff(backoff)
                                    onAttempt { attempt, delayMs ->
                                        val baseDelay = (backoff.initialDelayMs *
                                                backoff.multiplier.pow((attempt - 1).toDouble())
                                                ).toLong().coerceAtMost(backoff.maxDelayMs)
                                        val baseSecs = ((baseDelay) / 100L).toFloat() / 10f
                                        val baseSecsStr =
                                            ((kotlin.math.round(baseSecs * 10f)) / 10f).toString()
                                        val actualDelay = delayMs ?: 0L
                                        val actualSecs = (actualDelay / 100L).toFloat() / 10f
                                        val actualSecsStr =
                                            ((kotlin.math.round(actualSecs * 10f)) / 10f).toString()
                                        appendLog("[info] Attempt #$attempt (base: ${baseSecsStr}s, actual: ${actualSecsStr}s)")
                                    }
                                    onResult { attempt, result ->
                                        appendLog(
                                            "[info] Result at #$attempt: ${
                                                describeResult(
                                                    result
                                                )
                                            }"
                                        )
                                    }
                                    onComplete { attempts, durationMs, outcome ->
                                        val secs = (durationMs / 100L).toFloat() / 10f
                                        val secsStr =
                                            ((kotlin.math.round(secs * 10f)) / 10f).toString()
                                        appendLog(
                                            "[done] Completed after $attempts attempts in ${secsStr}s: ${
                                                describeOutcome(
                                                    outcome
                                                )
                                            }"
                                        )
                                    }
                                }

                                // Start countdown ticker respecting pause
                                scope.launch {
                                    while (isRunning && remainingMs > 0) {
                                        kotlinx.coroutines.delay(100)
                                        if (!isPaused) remainingMs =
                                            (remainingMs - 100).coerceAtLeast(0)
                                    }
                                }

                                // Start polling
                                handle = PollingEngine.startPolling(config) { outcome ->
                                    appendLog("[done] Final Outcome: ${describeOutcome(outcome)}")
                                    isRunning = false
                                    isPaused = false
                                    remainingMs = 0
                                    handle = null
                                }
                            } else {
                                // Toggle pause/resume
                                handle?.let {
                                    if (isPaused) {
                                        scope.launch { PollingEngine.resume(it.id) }
                                        isPaused = false
                                    } else {
                                        scope.launch { PollingEngine.pause(it.id) }
                                        isPaused = true
                                    }
                                }
                            }
                        }
                    )
                    Spacer(Modifier.weight(1f))
                    val secs = (remainingMs / 100L).toFloat() / 10f
                    val secsStr = ((kotlin.math.round(secs * 10f)) / 10f).toString()
                    Text(
                        text = if (isRunning) "${secsStr}s left" + if (isPaused) " (paused)" else "" else "",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Medium
                    )

                    // Stop button
                    GlowingButton(
                        enabled = isRunning,
                        text = "Stop",
                        onClick = {
                            handle?.let { h ->
                                scope.launch { PollingEngine.cancel(h) }
                            }
                            isRunning = false
                            isPaused = false
                            handle = null
                            remainingMs = 0
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Editable Properties panel (not in logs)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showProperties = !showProperties },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        (if (showProperties) "▼ " else "▶ ") + "Properties",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                if (showProperties) {
                    HorizontalDivider(
                        Modifier.padding(vertical = 6.dp),
                        DividerDefaults.Thickness, MaterialTheme.colorScheme.outline
                    )
                    PropertiesCard(
                        initialDelayText = initialDelayText,
                        onInitialChange = { initialDelayText = it },
                        maxDelayText = maxDelayText,
                        onMaxDelayChange = { maxDelayText = it },
                        multiplierText = multiplierText,
                        onMultiplierChange = { multiplierText = it },
                        jitterText = jitterText,
                        onJitterChange = { jitterText = it },
                        maxAttemptsText = maxAttemptsText,
                        onMaxAttemptsChange = { maxAttemptsText = it },
                        overallTimeoutText = overallTimeoutText,
                        onOverallTimeoutChange = { overallTimeoutText = it },
                        perAttemptTimeoutText = perAttemptTimeoutText,
                        onPerAttemptTimeoutChange = { perAttemptTimeoutText = it }
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        GlowingButton(
                            enabled = isRunning && handle != null,
                            text = "Apply Backoff",
                            onClick = {
                                fun appendLog(msg: String) {
                                    scope.launch { logs.add(msg) }
                                }

                                val initialDelay = initialDelayText.toLongOrNull()
                                val maxDelay = maxDelayText.toLongOrNull()
                                val multiplier = multiplierText.toDoubleOrNull()
                                val jitter = jitterText.toDoubleOrNull()
                                val maxAttempts = maxAttemptsText.toIntOrNull()
                                val overallTimeout = overallTimeoutText.toLongOrNull()
                                val perAttemptTimeout =
                                    perAttemptTimeoutText.trim().ifEmpty { null }?.toLongOrNull()
                                if (initialDelay == null || maxDelay == null || multiplier == null || jitter == null || maxAttempts == null || overallTimeout == null || (perAttemptTimeoutText.isNotEmpty() && perAttemptTimeout == null)) {
                                    appendLog("[error] Invalid properties; cannot apply backoff.")
                                    return@GlowingButton
                                }
                                val newPolicy = try {
                                    BackoffPolicy(
                                        initialDelayMs = initialDelay,
                                        maxDelayMs = maxDelay,
                                        multiplier = multiplier,
                                        jitterRatio = jitter,
                                        maxAttempts = maxAttempts,
                                        overallTimeoutMs = overallTimeout,
                                        perAttemptTimeoutMs = perAttemptTimeout,
                                    )
                                } catch (t: Throwable) {
                                    appendLog("[error] ${t.message}")
                                    return@GlowingButton
                                }
                                handle?.let { h ->
                                    scope.launch {
                                        PollingEngine.updateBackoff(
                                            h.id,
                                            newPolicy
                                        )
                                    }
                                }
                                appendLog("[info] Applied new backoff policy at runtime.")
                            }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Terminal Log view
                TerminalLog(modifier = Modifier.weight(1f), logs = logs)
            }
        }
    }
}