package `in`.androidplay.pollingengine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.round


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
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    suffix: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            label = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            placeholder = { if (placeholder != null) Text(placeholder) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            trailingIcon = {
                if (suffix != null) {
                    Text(
                        suffix,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            },
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth()
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                color = if (isError) Color(0xFFFF8A80) else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    )
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

@Composable
private fun ControlPanel(
    isRunning: Boolean,
    isPaused: Boolean,
    remainingMs: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit
) {
    val secs = (remainingMs / 100L).toFloat() / 10f
    val secsStr = ((round(secs * 10f)) / 10f).toString()
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isRunning) "${secsStr}s left" + if (isPaused) " (paused)" else "" else "Not running",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleLarge
            )
            if (isRunning) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    GlowingButton(
                        enabled = true,
                        text = if (isPaused) "Resume" else "Pause",
                        onClick = onPause
                    )
                    GlowingButton(
                        enabled = true,
                        text = "Stop",
                        onClick = onStop
                    )
                }
            } else {
                GlowingButton(
                    enabled = true,
                    text = "Start Polling",
                    onClick = onStart
                )
            }
        }
    }
}

// ------- Main App -------

@Composable
@Preview
fun App() {
    val viewModel = remember { PollingViewModel() }
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    val retryStrategies = listOf(
        "Always",
        "Never",
        "Any timeout"
    )

    PollingEngineTheme {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
            ) {
                item {
                    Column(
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

                        ControlPanel(
                            isRunning = uiState.isRunning,
                            isPaused = uiState.isPaused,
                            remainingMs = uiState.remainingMs,
                            onStart = { viewModel.dispatch(PollingIntent.StartPolling) },
                            onPause = { viewModel.dispatch(PollingIntent.PauseOrResumePolling) },
                            onStop = { viewModel.dispatch(PollingIntent.StopPolling) }
                        )

                        Spacer(Modifier.height(24.dp))

                        // Editable Properties panel (not in logs)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.dispatch(PollingIntent.ToggleProperties) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (if (uiState.showProperties) "▼ " else "▶ ") + "Basic Setup",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                if (uiState.showProperties) {
                                    Text(
                                        "Configure delays, timeouts and retry strategy.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (uiState.showProperties) {
                            HorizontalDivider(
                                Modifier.padding(vertical = 12.dp),
                                DividerDefaults.Thickness,
                                MaterialTheme.colorScheme.outline
                            )
                            Surface(
                                tonalElevation = 3.dp,
                                shadowElevation = 2.dp,
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = 0.92f
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    SectionHeader("Delays & Backoff")
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        LabeledField(
                                            "Initial",
                                            uiState.initialDelayText,
                                            { viewModel.dispatch(PollingIntent.UpdateInitialDelay(it)) },
                                            modifier = Modifier.weight(1f),
                                            placeholder = "e.g. 500",
                                            suffix = "ms",
                                            keyboardType = KeyboardType.Number
                                        )
                                        LabeledField(
                                            "Max",
                                            uiState.maxDelayText,
                                            { viewModel.dispatch(PollingIntent.UpdateMaxDelay(it)) },
                                            modifier = Modifier.weight(1f),
                                            placeholder = "e.g. 5000",
                                            suffix = "ms",
                                            keyboardType = KeyboardType.Number
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        LabeledField(
                                            "Multiplier",
                                            uiState.multiplierText,
                                            { viewModel.dispatch(PollingIntent.UpdateMultiplier(it)) },
                                            modifier = Modifier.weight(1f),
                                            placeholder = "e.g. 1.8",
                                            keyboardType = KeyboardType.Decimal
                                        )
                                        LabeledField(
                                            "Jitter",
                                            uiState.jitterText,
                                            { viewModel.dispatch(PollingIntent.UpdateJitter(it)) },
                                            modifier = Modifier.weight(1f),
                                            placeholder = "e.g. 0.15",
                                            keyboardType = KeyboardType.Decimal
                                        )
                                    }

                                    SectionDivider()
                                    SectionHeader("Timeouts & Attempts")
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        LabeledField(
                                            "Max Attempts",
                                            uiState.maxAttemptsText,
                                            { viewModel.dispatch(PollingIntent.UpdateMaxAttempts(it)) },
                                            modifier = Modifier.weight(1f),
                                            placeholder = "e.g. 12",
                                            keyboardType = KeyboardType.Number
                                        )
                                        LabeledField(
                                            "Overall",
                                            uiState.overallTimeoutText,
                                            {
                                                viewModel.dispatch(
                                                    PollingIntent.UpdateOverallTimeout(
                                                        it
                                                    )
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            placeholder = "e.g. 30000",
                                            suffix = "ms",
                                            keyboardType = KeyboardType.Number
                                        )
                                    }
                                    LabeledField(
                                        "Per-attempt",
                                        uiState.perAttemptTimeoutText,
                                        {
                                            viewModel.dispatch(
                                                PollingIntent.UpdatePerAttemptTimeout(
                                                    it
                                                )
                                            )
                                        },
                                        placeholder = "empty = unlimited",
                                        suffix = "ms",
                                        keyboardType = KeyboardType.Number
                                    )

                                    // Live update button
                                    GlowingButton(
                                        enabled = uiState.isRunning,
                                        text = "Apply Backoff at Runtime",
                                        onClick = { viewModel.dispatch(PollingIntent.ApplyBackoffAtRuntime) }
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Retry Strategy inside Basic setup
                            Surface(
                                tonalElevation = 2.dp,
                                shadowElevation = 1.dp,
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = 0.7f
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        "Retry Strategy",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        retryStrategies.forEachIndexed { idx, label ->
                                            val selected = uiState.retryStrategyIndex == idx
                                            Button(
                                                onClick = {
                                                    viewModel.dispatch(
                                                        PollingIntent.UpdateRetryStrategy(
                                                            idx
                                                        )
                                                    )
                                                },
                                                enabled = true,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                                ),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            ) {
                                                Text(
                                                    label,
                                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(24.dp))
                }
                items(items = uiState.logs, key = { it.id }) { line ->
                    LogEntryCard(line = line.text)
                }
            }
        }
    }
}
