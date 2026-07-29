package com.gamevault.app.domain.model

/**
 * A route/ending within a game — like chapters in Mihon.
 * Tracks progress through different paths, endings, or seasons.
 */
data class GameRoute(
    val id: Long = 0,
    val gameId: Long = 0,
    val name: String,
    val progress: Int = 0,        // 0..100
    val status: RouteStatus = RouteStatus.LOCKED,
    val order: Int = 0,
    val notes: String? = null,
)

enum class RouteStatus(val displayName: String) {
    LOCKED("Locked"),
    UNLOCKED("Unlocked"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed");
}
