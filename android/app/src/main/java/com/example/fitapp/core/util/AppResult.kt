package com.example.fitapp.core.util

import com.example.fitapp.core.network.ApiFailure

/** Outcome of a repository call that can fail in ways the UI should explain. */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>

    data class Failure(val failure: ApiFailure) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> this
    }

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value

val <T> AppResult<T>.isSuccess: Boolean get() = this is AppResult.Success
