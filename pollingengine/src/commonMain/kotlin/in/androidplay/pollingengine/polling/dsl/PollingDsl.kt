package `in`.androidplay.pollingengine.polling.dsl

import `in`.androidplay.pollingengine.models.Error
import `in`.androidplay.pollingengine.polling.RetryPredicates

/** Restricts the implicit receiver scope of the polling DSL builders. */
@DslMarker
public annotation class PollingDsl

/**
 * Ready-made retry predicates for [PollBuilder.retryWhen]. A predicate receives the mapped
 * [Error] from a failed attempt and returns `true` to keep polling, `false` to give up.
 *
 * ```
 * Polling.poll { api.status() }.until { it.done }.retryWhen(Retry.networkOrServer)
 * ```
 */
public object Retry {
    /** Retry every error (the default). */
    public val always: (Error?) -> Boolean = RetryPredicates.always

    /** Never retry — the first error ends the poll. */
    public val never: (Error?) -> Boolean = RetryPredicates.never

    /** Retry transient network, server, and timeout errors; give up on the rest. */
    public val networkOrServer: (Error?) -> Boolean = RetryPredicates.networkOrServerOrTimeout
}
