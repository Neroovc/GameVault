package com.gamevault.app.domain.model

/**
 * A play session — tracks when and how long you played.
 */
data class PlaySession(
    val id: Long = 0,
    val gameId: Long = 0,
    val routeId: Long? = null,
    val startTime: Long,
    val endTime: Long? = null,   // null = still playing
    val durationMinutes: Long? = null,
    val notes: String? = null,
)
