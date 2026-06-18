package `in`.androidplay.pollingengine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun DemoCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SubscriberTile(
    title: String,
    filterLabel: String,
    subscribed: Boolean,
    latest: Int?,
    count: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (subscribed) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Caption(filterLabel)
            Text(
                text = latest?.toString() ?: "—",
                fontFamily = FontFamily.Monospace,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Caption("received: $count")
            GlowingButton(
                enabled = true,
                text = if (subscribed) "Unsubscribe" else "Subscribe",
                onClick = onToggle,
            )
        }
    }
}

@Composable
fun SharedObserveScreen() {
    val viewModel = remember { SharedObserveViewModel() }
    val state by viewModel.uiState.collectAsState()
    DisposableEffect(Unit) {
        onDispose { viewModel.dispose() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Shared & Observe",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Caption("Streaming + multiplexing APIs on a fixed cadence.")
            }
        }

        // ---- Shared multiplexed session ----
        item {
            DemoCard {
                Text(
                    "shared() — one fetch / tick, many subscribers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Caption(
                    "A single upstream poll runs every ${state.intervalMs / 1000}s via " +
                            "every(). Both tiles below read from the SAME upstream " +
                            "via stream(filter) — the fetch count proves exactly one network call per " +
                            "tick no matter how many subscribers are attached.",
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Caption("Upstream fetches (shared)")
                            Text(
                                "tick #${state.upstreamFetchCount}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Caption("Live subscribers")
                            Text(
                                listOfNotNull(
                                    "A".takeIf { state.subscriberA },
                                    "B".takeIf { state.subscriberB },
                                ).joinToString("+").ifEmpty { "none" },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SubscriberTile(
                        title = "Subscriber A",
                        filterLabel = "filter { it % 2 == 0 }",
                        subscribed = state.subscriberA,
                        latest = state.aLatest,
                        count = state.aCount,
                        onToggle = { viewModel.dispatch(SharedObserveIntent.ToggleSubscriberA) },
                        modifier = Modifier.weight(1f),
                    )
                    SubscriberTile(
                        title = "Subscriber B",
                        filterLabel = "filter { it % 3 == 0 }",
                        subscribed = state.subscriberB,
                        latest = state.bLatest,
                        count = state.bCount,
                        onToggle = { viewModel.dispatch(SharedObserveIntent.ToggleSubscriberB) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Caption(
                    "Subscriber-driven: the loop starts on the first subscriber and stops " +
                            "${state.stopTimeoutMs / 1000}s after the last one leaves (WhileSubscribed). " +
                            "Unsubscribe both and watch the tick counter freeze, then restart on re-subscribe.",
                )
            }
        }

        // ---- Observe continuous stream ----
        item {
            DemoCard {
                Text(
                    "collect()/asFlow() — continuous value stream",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Caption(
                    "Emits every successful tick (1/s) instead of converging to a single outcome, " +
                            "and auto-completes via stopWhen once the value passes " +
                            "${state.observeStopAfter} (the stopping tick is not emitted).",
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlowingButton(
                        enabled = !state.observeRunning,
                        text = "Start observe()",
                        onClick = { viewModel.dispatch(SharedObserveIntent.StartObserve) },
                    )
                    GlowingButton(
                        enabled = state.observeRunning,
                        text = "Stop",
                        onClick = { viewModel.dispatch(SharedObserveIntent.StopObserve) },
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Caption(if (state.observeRunning) "streaming…" else "idle / completed")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.observeValues.joinToString("  →  ")
                                .ifEmpty { "(no values yet)" },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
