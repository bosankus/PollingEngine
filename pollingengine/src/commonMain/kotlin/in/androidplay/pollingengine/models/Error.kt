package `in`.androidplay.pollingengine.models

/**
 * Simple error model used by the polling engine.
 */
public data class Error(
    val code: Int,
    val message: String? = null,
)
