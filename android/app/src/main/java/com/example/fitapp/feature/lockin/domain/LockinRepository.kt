package com.example.fitapp.feature.lockin.domain

import com.example.fitapp.core.util.AppResult
import kotlinx.coroutines.flow.Flow

interface LockinRepository {
    /** Every program, in the server's order. Days are empty until the detail has been downloaded. */
    fun observePrograms(): Flow<List<LockinProgram>>

    fun observeProgram(id: String): Flow<LockinProgram?>

    /** Downloads the list and every program's days into Room. Everything keeps working offline afterwards. */
    suspend fun refresh(): AppResult<Unit>
}

/** Settings of the Lock-in experience that live on this device. */
interface LockinPreferences {
    /** The health warning is shown once; ticking the box and continuing sets this. */
    val warningAcknowledged: Flow<Boolean>

    /** Ask Android to silence notifications during a session (needs the user's permission). */
    val useDoNotDisturb: Flow<Boolean>

    /** Pin the screen so the app cannot be left by accident (the system asks the user to confirm). */
    val pinScreen: Flow<Boolean>

    suspend fun acknowledgeWarning()

    suspend fun setUseDoNotDisturb(enabled: Boolean)

    suspend fun setPinScreen(enabled: Boolean)
}

/** Turns Do Not Disturb on for a session and puts the user's previous setting back afterwards. */
interface DoNotDisturb {
    /** Whether the user has granted the app access to the notification policy. */
    fun hasAccess(): Boolean

    /** Silences notifications, remembering what was set before. Does nothing without access. */
    suspend fun enable()

    /** Restores the remembered setting. Safe to call when nothing was changed. */
    suspend fun restore()
}
