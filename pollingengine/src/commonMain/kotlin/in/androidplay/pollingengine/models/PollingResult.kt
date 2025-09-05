package `in`.androidplay.pollingengine.models

/**
 * Generic result wrapper used by the polling engine to represent remote/API state.
 */
public sealed class PollingResult<out T> {
    public data class Success<T>(val data: T) : PollingResult<T>()
    public data class Failure(val error: Error) : PollingResult<Nothing>()
    public data object Cancelled : PollingResult<Nothing>()
    public data object Waiting : PollingResult<Nothing>()
    public data object Unknown : PollingResult<Nothing>()
}
