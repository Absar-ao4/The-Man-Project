package com.example.fitapp.core.sync

import com.example.fitapp.core.network.AppJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import java.time.Instant

/** A local row waiting to go to the server, with the exact JSON that will be sent. */
class PushRow(
    val id: String,
    /** The row's updatedAt when it was read, so a later edit is not marked synced by mistake. */
    val updatedAt: Long,
    val tombstone: Boolean,
    val json: JsonElement,
)

/**
 * Knows how one entity moves between its Room tables and the sync wire format. The engine owns the
 * protocol (batching, ordering, cursors, retries); adapters only translate and apply.
 */
interface SyncAdapter {
    /** The name the server uses for this entity, e.g. "meal_items". */
    val entity: String

    /** Rows still to send, deletions included. Rows being rejected or already synced are not listed. */
    suspend fun pending(userId: String): List<PushRow>

    /** The server stored [row] (or already had a newer version). Safe to call if it was edited meanwhile. */
    suspend fun onAccepted(row: PushRow)

    /** The server refused [row] as sent. It is parked (REJECTED) instead of being retried forever. */
    suspend fun onRejected(row: PushRow)

    /** The server merged [row] into an existing row with another id; drop the local duplicate. */
    suspend fun onRemapped(row: PushRow) = Unit

    /** Applies rows pulled from the server. Rows with unsent local edits are left alone. Runs in a transaction. */
    suspend fun applyPulled(
        userId: String,
        rows: List<JsonElement>,
    )
}

internal fun Long.toIso(): String = Instant.ofEpochMilli(this).toString()

internal fun String.toEpochMillis(): Long = Instant.parse(this).toEpochMilli()

internal fun String?.toEpochMillisOrNull(): Long? = this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

internal fun <T> encodeRow(
    serializer: KSerializer<T>,
    row: T,
): JsonElement = AppJson.encodeToJsonElement(serializer, row)

/** Rows that cannot be read are skipped (and would show up as a bug in the backend contract), never fatal. */
internal fun <T> decodeRows(
    serializer: KSerializer<T>,
    rows: List<JsonElement>,
): List<T> = rows.mapNotNull { runCatching { AppJson.decodeFromJsonElement(serializer, it) }.getOrNull() }
