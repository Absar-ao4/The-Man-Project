package com.example.fitapp.core.network

/** Everything that can go wrong talking to the backend, with a message safe to show the user. */
sealed class ApiFailure(open val message: String) {
    data object Offline : ApiFailure("You seem to be offline. Check your connection and try again.")

    data object Timeout : ApiFailure("The server took too long to respond. Please try again.")

    data class Unauthorized(override val message: String = "Your session expired. Please log in again.") :
        ApiFailure(message)

    data class Invalid(
        override val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : ApiFailure(message)

    data class Conflict(
        override val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : ApiFailure(message)

    data object NotFound : ApiFailure("We could not find that.")

    data object RateLimited : ApiFailure("Too many attempts. Please wait a minute and try again.")

    data class Server(override val message: String = "The server had a problem. Please try again shortly.") :
        ApiFailure(message)

    data class Unknown(override val message: String = "Something unexpected happened. Please try again.") :
        ApiFailure(message)
}
