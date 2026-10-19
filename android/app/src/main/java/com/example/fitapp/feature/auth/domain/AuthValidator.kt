package com.example.fitapp.feature.auth.domain

/** Instant client-side checks that mirror the backend's rules. Each returns an error message or null. */
object AuthValidator {
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val USERNAME = Regex("^[A-Za-z0-9_]{3,20}$")

    const val MIN_PASSWORD = 8
    const val MAX_PASSWORD = 72
    const val MAX_NAME = 80

    fun email(value: String): String? = if (EMAIL.matches(value.trim()) && value.length <= 254) null else "Enter a valid email address"

    fun password(value: String): String? =
        when {
            value.length < MIN_PASSWORD -> "Use at least $MIN_PASSWORD characters"
            value.length > MAX_PASSWORD -> "Use at most $MAX_PASSWORD characters"
            else -> null
        }

    fun loginPassword(value: String): String? = if (value.isEmpty()) "Enter your password" else null

    fun username(value: String): String? =
        if (USERNAME.matches(value.trim())) null else "3 to 20 letters, digits or underscores"

    fun name(value: String): String? =
        when {
            value.isBlank() -> "Enter your name"
            value.trim().length > MAX_NAME -> "Use at most $MAX_NAME characters"
            else -> null
        }
}
