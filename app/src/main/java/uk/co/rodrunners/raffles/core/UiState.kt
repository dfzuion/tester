package uk.co.rodrunners.raffles.core

/** Every screen renders one of these. There is no fourth, blank case. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Empty(val title: String, val body: String, val actionLabel: String? = null) : UiState<Nothing>
    data class Error(val error: AppError) : UiState<Nothing>
    data class Success<T>(val data: T, val refreshing: Boolean = false) : UiState<T>
}

/** User-facing failure with a message written for a customer, not a developer. */
data class AppError(
    val title: String,
    val message: String,
    val retryable: Boolean = true,
    val cause: Throwable? = null,
)

object Errors {
    val network = AppError(
        title = "No connection",
        message = "We couldn't reach Rod Runners. Check your signal and try again — nothing has been charged.",
    )
    val unauthenticated = AppError(
        title = "Session expired",
        message = "Log in again to continue.",
        retryable = false,
    )

    fun from(t: Throwable): AppError {
        val message = t.message.orEmpty()
        return when {
            message.contains("UNAVAILABLE", true) ||
                message.contains("network", true) ||
                message.contains("timeout", true) -> network
            message.contains("UNAUTHENTICATED", true) ||
                message.contains("PERMISSION_DENIED", true) -> unauthenticated
            message.isNotBlank() -> AppError("Something went wrong", message, cause = t)
            else -> AppError("Something went wrong", "Please try again in a moment.", cause = t)
        }
    }
}
