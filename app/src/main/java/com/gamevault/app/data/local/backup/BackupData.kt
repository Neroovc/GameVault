package com.gamevault.app.data.local.backup

import com.gamevault.app.domain.model.Collection
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameEngine
import com.gamevault.app.domain.model.GameRoute
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.domain.model.PlaySession
import com.gamevault.app.domain.model.RouteStatus
import com.gamevault.app.domain.model.SourceType
import com.gamevault.app.domain.model.Tag

/**
 * Serializable snapshot of the entire library — used by BackupManager.
 */
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val games: List<BackupGame> = emptyList(),
    val collections: List<BackupCollection> = emptyList(),
    val tags: List<BackupTag> = emptyList(),
)

data class BackupGame(
    val id: Long = 0,
    val title: String,
    val coverUrl: String? = null,
    val description: String? = null,
    val developer: String? = null,
    val engine: String? = null,
    val version: String? = null,
    val status: String = GameStatus.NOT_STARTED.name,
    val personalRating: Float? = null,
    val f95Rating: Float? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastPlayed: Long? = null,
    val playTimeMinutes: Long = 0,
    val notes: String? = null,
    val f95Url: String? = null,
    val sourceType: String = SourceType.MANUAL.name,
    val sourceUrl: String? = null,
    val routes: List<BackupRoute> = emptyList(),
    val sessions: List<BackupSession> = emptyList(),
    val tagNames: List<String> = emptyList(),
    val collectionNames: List<String> = emptyList(),
)

data class BackupRoute(
    val id: Long = 0,
    val name: String,
    val progress: Int = 0,
    val status: String = RouteStatus.UNLOCKED.name,
    val order: Int = 0,
)

data class BackupSession(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val durationMinutes: Long? = null,
)

data class BackupCollection(
    val name: String,
    val description: String? = null,
    val color: Long? = null,
    val order: Int = 0,
)

data class BackupTag(
    val name: String,
)

// ── Converters ────────────────────────────────────────────

fun BackupData.toDomain(
    existingCollections: List<Collection> = emptyList(),
    existingTags: List<Tag> = emptyList(),
): BackupImportResult {
    val importedCollections = collections.map { bc ->
        Collection(name = bc.name, description = bc.description, color = bc.color, order = bc.order)
    }
    val importedTags = tags.map { bt -> Tag(name = bt.name) }

    val importedGames = games.map { bg ->
        Game(
            title = bg.title,
            coverUrl = bg.coverUrl,
            description = bg.description,
            developer = bg.developer,
            engine = bg.engine?.let { runCatching { GameEngine.valueOf(it) }.getOrNull() },
            version = bg.version,
            status = runCatching { GameStatus.valueOf(bg.status) }.getOrElse { GameStatus.NOT_STARTED },
            personalRating = bg.personalRating,
            f95Rating = bg.f95Rating,
            dateAdded = bg.dateAdded,
            lastPlayed = bg.lastPlayed,
            playTimeMinutes = bg.playTimeMinutes,
            notes = bg.notes,
            f95Url = bg.f95Url,
            sourceType = runCatching { SourceType.valueOf(bg.sourceType) }.getOrElse { SourceType.MANUAL },
            sourceUrl = bg.sourceUrl,
            routes = bg.routes.map { br ->
                GameRoute(
                    name = br.name,
                    progress = br.progress,
                    status = runCatching { RouteStatus.valueOf(br.status) }.getOrElse { RouteStatus.UNLOCKED },
                    order = br.order,
                )
            },
            tags = bg.tagNames.map { name -> existingTags.find { it.name == name } ?: Tag(name = name) },
            collections = bg.collectionNames.map { name ->
                existingCollections.find { it.name.equals(name, ignoreCase = true) }
                    ?: Collection(name = name)
            },
        )
    }

    return BackupImportResult(
        games = importedGames,
        collections = importedCollections,
        tags = importedTags,
    )
}

data class BackupImportResult(
    val games: List<Game>,
    val collections: List<Collection>,
    val tags: List<Tag>,
)
