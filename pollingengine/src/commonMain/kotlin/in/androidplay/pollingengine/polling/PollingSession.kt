package `in`.androidplay.pollingengine.polling

/**
 * Represents a running polling session created by the SDK.
 *
 * Consumers receive this minimal handle when starting a poll and can use its [id]
 * with the facade API to pause, resume, cancel, or update options.
 *
 * Note: Business logic and engine internals are intentionally hidden.
 */
public data class PollingSession(val id: String)