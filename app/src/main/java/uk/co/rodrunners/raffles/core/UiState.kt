package uk.co.rodrunners.raffles.core

import com.google.firebase.functions.FirebaseFunctionsException

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
        // Ask the exception for its code rather than pattern-matching a
        // message. A callable that fails an admin check comes back with a
        // perfectly good server-written reason, and collapsing it into "log in
        // again" sent us hunting a login problem that did not exist.
        val functionsCode = (t as? FirebaseFunctionsException)?.code
        if (functionsCode != null) {
            val serverMessage = t.message?.takeIf { it.isNotBlank() }
            return when (functionsCode) {
                FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                    // App Check rejections land here too, and they are not a
                    // login problem - so say what the server actually said.
                    AppError(
                        title = "Not signed in",
                        message = serverMessage ?: "Log in again to continue.",
                        retryable = false,
                        cause = t,
                    )
                FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                    AppError(
                        title = "Not allowed",
                        message = serverMessage ?: "You don't have permission to do that.",
                        retryable = false,
                        cause = t,
                    )
                FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                    AppError("Not ready", serverMessage ?: "That can't be done yet.", retryable = false, cause = t)
                FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                    AppError("Check that again", serverMessage ?: "Something in that form isn't right.", cause = t)
                FirebaseFunctionsException.Code.NOT_FOUND ->
                    AppError("Not found", serverMessage ?: "We couldn't find that.", retryable = false, cause = t)
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                    AppError("Too many attempts", serverMessage ?: "Wait a moment and try again.", cause = t)
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> network
                else -> AppError("Something went wrong", serverMessage ?: "Please try again in a moment.", cause = t)
            }
        }

        val message = t.message.orEmpty()
        return when {
            message.contains("UNAVAILABLE", true) ||
                message.contains("network", true) ||
                message.contains("timeout", true) -> network
            message.contains("UNAUTHENTICATED", true) -> unauthenticated
            message.isNotBlank() -> AppError("Something went wrong", message, cause = t)
            else -> AppError("Something went wrong", "Please try again in a moment.", cause = t)
        }
    }
}
