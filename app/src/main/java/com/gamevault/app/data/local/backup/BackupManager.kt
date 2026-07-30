package com.gamevault.app.data.local.backup

import android.content.Context
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.repository.GameRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/**
 * Handles JSON export/import of the entire game library.
 *
 * The exported JSON goes to the app's cache directory for sharing;
 * import reads from a user-provided file path.
 */
class BackupManager(
    private val context: Context,
    private val repository: GameRepository,
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Export all data to a JSON string.
     */
    suspend fun exportJson(): String {
        val allGames = repository.getAllGamesList()
        val allCollections = repository.getAllCollections()
        val allTags = repository.getAllTags()

        val backup = BackupData(
            games = allGames.map { game ->
                val routes = repository.getRoutesForGame(game.id)
                val sessions = repository.getSessionsForGame(game.id)
                BackupGame(
                    id = game.id,
                    title = game.title,
                    coverUrl = game.coverUrl,
                    description = game.description,
                    developer = game.developer,
                    engine = game.engine?.name,
                    version = game.version,
                    status = game.status.name,
                    personalRating = game.personalRating,
                    f95Rating = game.f95Rating,
                    dateAdded = game.dateAdded,
                    lastPlayed = game.lastPlayed,
                    playTimeMinutes = game.playTimeMinutes,
                    notes = game.notes,
                    f95Url = game.f95Url,
                    sourceType = game.sourceType.name,
                    sourceUrl = game.sourceUrl,
                    routes = routes.map { route ->
                        BackupRoute(
                            name = route.name,
                            progress = route.progress,
                            status = route.status.name,
                            order = route.order,
                        )
                    },
                    sessions = sessions.map { session ->
                        BackupSession(
                            startTime = session.startTime,
                            endTime = session.endTime,
                            durationMinutes = session.durationMinutes,
                        )
                    },
                    tagNames = game.tags.map { it.name },
                    collectionNames = game.collections.map { it.name },
                )
            },
            collections = allCollections.map { coll ->
                BackupCollection(
                    name = coll.name,
                    description = coll.description,
                    color = coll.color,
                    order = coll.order,
                )
            },
            tags = allTags.map { tag ->
                BackupTag(name = tag.name)
            },
        )

        return gson.toJson(backup)
    }

    /**
     * Write export to a file in the cache directory and return the File.
     */
    suspend fun exportToCache(): File {
        val json = exportJson()
        val file = File(context.cacheDir, "gamevault-backup.json")
        file.writeText(json)
        return file
    }

    /**
     * Import all data from a JSON file.
     * This is additive — existing data is preserved.
     * Returns the number of games imported.
     */
    suspend fun importFromFile(file: File): Int {
        val json = file.readText()
        return importFromJson(json)
    }

    /**
     * Import all data from a JSON string.
     */
    suspend fun importFromJson(json: String): Int {
        val backup = gson.fromJson(json, BackupData::class.java)
        val existingCollections = repository.getAllCollections()
        val existingTags = repository.getAllTags()
        val result = backup.toDomain(existingCollections, existingTags)

        var count = 0

        // Save new collections
        result.collections.forEach { coll ->
            val exists = existingCollections.any { it.name.equals(coll.name, ignoreCase = true) }
            if (!exists) {
                repository.saveCollection(coll)
            }
        }

        // Save new tags
        result.tags.forEach { tag ->
            val exists = existingTags.any { it.name.equals(tag.name, ignoreCase = true) }
            if (!exists) {
                repository.createTag(tag.name)
            }
        }

        // Refresh collections for matching IDs
        val allCollections = repository.getAllCollections()

        // Save games (collections & tags already resolved by toDomain)
        result.games.forEach { game ->
            val gameId = repository.saveGame(game)

            // Restore collection membership
            game.collections.forEach { coll ->
                val matched = allCollections.find { it.name.equals(coll.name, ignoreCase = true) }
                if (matched != null) {
                    repository.addGameToCollection(gameId, matched.id)
                }
            }

            count++
        }

        return count
    }
}
