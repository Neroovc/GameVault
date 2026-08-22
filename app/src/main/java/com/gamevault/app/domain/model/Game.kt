package com.gamevault.app.domain.model

/**
 * Core domain model for a game in the library.
 */
data class Game(
    val id: Long = 0,
    val title: String,
    val coverUrl: String? = null,
    val localCoverPath: String? = null,   // absolute path to a user-picked cover (device-local)
    val description: String? = null,
    val developer: String? = null,           // real creator, scraped from thread body
    val publisher: String? = null,           // thread OP / re-publisher, article[data-author]
    val engine: GameEngine? = null,
    val version: String? = null,
    val status: GameStatus = GameStatus.NOT_STARTED,
    val personalRating: Float? = null,       // 0.5..5.0, step 0.5
    val f95Rating: Float? = null,            // from F95Zone
    val dateAdded: Long = System.currentTimeMillis(),
    val lastPlayed: Long? = null,
    val lastChecked: Long? = null,           // last metadata check (refresh pass or update worker)
    val playTimeMinutes: Long = 0,
    val inLibrary: Boolean = true,           // soft flag; row stays when unmarked
    val notes: String? = null,
    val f95Url: String? = null,
    val sourceType: SourceType = SourceType.MANUAL,
    val sourceUrl: String? = null,
    val changelog: String? = null,
    val devLinks: List<String> = emptyList(),
    val downloadLinks: List<String> = emptyList(),
    val updateAvailable: Boolean = false,
    val updatesMuted: Boolean = false,
    // Consecutive checks with no version change; backs adaptive fetch pacing (capped at 3).
    val emptyChecks: Int = 0,
    val routes: List<GameRoute> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val collections: List<Collection> = emptyList(),
)

enum class GameStatus {
    NOT_STARTED,
    PLAYING,
    COMPLETED,
    REPLAYING,
    PAUSED,
    ABANDONED;

    val displayName: String
        get() = when (this) {
            NOT_STARTED -> "Not Started"
            PLAYING -> "Playing"
            COMPLETED -> "Completed"
            REPLAYING -> "Replaying"
            PAUSED -> "Paused"
            ABANDONED -> "Abandoned"
        }
}

enum class GameEngine(val displayName: String) {
    RENPY("Ren'Py"),
    RPGM("RPG Maker"),
    UNITY("Unity"),
    UNREAL("Unreal"),
    HTML("HTML"),
    FLASH("Flash"),
    JAVA("Java"),
    TWINE("Twine"),
    OTHER("Other"),
    UNKNOWN("Unknown");
}

enum class SourceType(val displayName: String) {
    F95ZONE("F95Zone"),
    STEAM("Steam"),
    VNDB("VNDB"),
    ITCHIO("Itch.io"),
    DLSITE("DLSite"),
    MANUAL("Manual"),
    RYUU_GAMES("RyuuGames"),
    FAP_FOR_FUN("FapForFun");
}
