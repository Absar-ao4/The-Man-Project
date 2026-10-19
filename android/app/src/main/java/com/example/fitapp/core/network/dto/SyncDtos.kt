package com.example.fitapp.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** One entity per push; rows are that entity's JSON (see core/sync/SyncRows.kt). */
@Serializable
data class PushRequestDto(
    val entity: String,
    val rows: List<JsonElement>,
)

@Serializable
data class RejectedRowDto(
    val id: String? = null,
    val reason: String = "",
)

/** The server kept this device's row under another id because that day/food already existed. */
@Serializable
data class IdRemapDto(
    val from: String,
    val to: String,
)

@Serializable
data class PushResponseDto(
    val entity: String,
    val accepted: List<String> = emptyList(),
    val stale: List<String> = emptyList(),
    val rejected: List<RejectedRowDto> = emptyList(),
    val remaps: List<IdRemapDto> = emptyList(),
    val serverTime: String? = null,
    /** XP, badges and level changes. Read from Phase 6 on. */
    val celebrations: JsonElement? = null,
)

@Serializable
data class PullResponseDto(
    val serverTime: String,
    val hasMore: Boolean = false,
    val changes: Map<String, List<JsonElement>> = emptyMap(),
)
