package `in`.androidplay.pollingengine.polling

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
        config: PollingConfig<T>,
        onComplete: (PollingOutcome<T>) -> Unit,
    ): PollingSession =
        PollingEngine.startPolling(config, onComplete).let { PollingSession(it.id) }

    override suspend fun <T> run(config: PollingConfig<T>): PollingOutcome<T> =
        PollingEngine.pollUntil(config)

    override suspend fun <T> compose(vararg configs: PollingConfig<T>): PollingOutcome<T> =
        PollingEngine.compose(*configs)
}

