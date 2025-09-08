package `in`.androidplay.pollingengine.polling

import `in`.androidplay.pollingengine.polling.builder.PollingConfigBuilder
import kotlinx.coroutines.flow.Flow

/**
 * Public facade instance for consumers. Delegates to the internal engine.
 *
 * This is the single entry-point to start, control, and run polling operations.
 */
public object Polling : PollingApi {
    override fun activePollsCount(): Int = PollingEngine.activePollsCount()
    override suspend fun listActiveIds(): List<String> = PollingEngine.listActiveIds()

    override suspend fun cancel(id: String): Unit = PollingEngine.cancel(id)
    override suspend fun cancel(session: PollingSession): Unit = PollingEngine.cancel(session.id)
    override suspend fun cancelAll(): Unit = PollingEngine.cancelAll()
    override suspend fun shutdown(): Unit = PollingEngine.shutdown()

    override suspend fun pause(id: String): Unit = PollingEngine.pause(id)
    override suspend fun resume(id: String): Unit = PollingEngine.resume(id)
    override suspend fun updateBackoff(id: String, newPolicy: BackoffPolicy): Unit =
        PollingEngine.updateBackoff(id, newPolicy)

    override fun <T> startPolling(
        config: PollingConfig<T>
    ): Flow<PollingOutcome<T>> = PollingEngine.startPolling(config)

    override fun <T> startPolling(
        builder: PollingConfigBuilder<T>.() -> Unit
    ): Flow<PollingOutcome<T>> {
        val config = PollingConfigBuilder<T>().apply(builder).build()
        return startPolling(config)
    }

    override suspend fun <T> run(config: PollingConfig<T>): PollingOutcome<T> =
        PollingEngine.pollUntil(config)

    override suspend fun <T> compose(vararg configs: PollingConfig<T>): PollingOutcome<T> =
        PollingEngine.compose(*configs)
}
