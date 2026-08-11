package com.gamevault.app.data.local

import android.content.Context
import android.net.Uri
import com.gamevault.app.data.local.dao.CollectionDao
import com.gamevault.app.data.local.dao.GameCollectionDao
import com.gamevault.app.data.local.dao.GameDao
import com.gamevault.app.data.local.dao.GameRouteDao
import com.gamevault.app.data.local.dao.GameTagDao
import com.gamevault.app.data.local.dao.PlaySessionDao
import com.gamevault.app.data.local.dao.TagDao
import com.gamevault.app.data.local.entity.CollectionEntity
import com.gamevault.app.data.local.entity.GameCollectionCrossRef
import com.gamevault.app.data.local.entity.GameEntity
import com.gamevault.app.data.local.entity.GameRouteEntity
import com.gamevault.app.data.local.entity.GameTagCrossRef
import com.gamevault.app.data.local.entity.PlaySessionEntity
import com.gamevault.app.data.local.entity.TagEntity
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader

data class BackupData(
    val version: Int = 1,
    val exportDate: Long = System.currentTimeMillis(),
    val games: List<BackupGame> = emptyList(),
    val collections: List<BackupCollection> = emptyList(),
    val gameCollections: List<BackupGameCollection> = emptyList(),
    val playSessions: List<BackupPlaySession> = emptyList(),
    val routes: List<BackupRoute> = emptyList(),
    val tags: List<BackupTag> = emptyList(),
    val gameTags: List<BackupGameTag> = emptyList(),
)

data class BackupGame(
    val title: String,
    val inLibrary: Boolean = true,
    val coverUrl: String? = null,
    val description: String? = null,
    val developer: String? = null,
    val engine: String? = null,
    val version: String? = null,
    val status: String = "NOT_STARTED",
    val personalRating: Float? = null,
    val f95Rating: Float? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastPlayed: Long? = null,
    val playTimeMinutes: Long = 0,
    val notes: String? = null,
    val f95Url: String? = null,
    val sourceType: String = "MANUAL",
    val sourceUrl: String? = null,
    val changelog: String? = null,
    val devLinks: List<String> = emptyList(),
    val downloadLinks: List<String> = emptyList(),
)

data class BackupCollection(
    val name: String,
    val description: String? = null,
    val color: Long? = null,
    val order: Int = 0,
)

data class BackupGameCollection(
    val gameIndex: Int,
    val collectionIndex: Int,
)

data class BackupPlaySession(
    val gameIndex: Int,
    val routeIndex: Int? = null,
    val startTime: Long,
    val endTime: Long? = null,
    val durationMinutes: Long? = null,
    val notes: String? = null,
)

data class BackupRoute(
    val gameIndex: Int,
    val name: String,
    val progress: Int = 0,
    val status: String = "LOCKED",
    val order: Int = 0,
    val notes: String? = null,
)

data class BackupTag(
    val name: String,
)

data class BackupGameTag(
    val gameIndex: Int,
    val tagIndex: Int,
)

data class ImportResult(
    val success: Boolean,
    val message: String,
    val gamesImported: Int,
    val collectionsImported: Int,
)

class GameVaultBackup(
    private val gameDao: GameDao,
    private val routeDao: GameRouteDao,
    private val sessionDao: PlaySessionDao,
    private val tagDao: TagDao,
    private val collectionDao: CollectionDao,
    private val gameCollectionDao: GameCollectionDao,
    private val gameTagDao: GameTagDao,
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportToJson(): String {
        val allGames = gameDao.getAllGamesUnfiltered()
        val allRoutes = routeDao.getAllRoutes()
        val allSessions = sessionDao.getAllSessions()
        val allTags = tagDao.getAllTags()
        val allCollections = collectionDao.getAllCollections()
        val allGameCollections = gameCollectionDao.getAllCrossRefs()
        val allGameTags = gameTagDao.getAll()

        // Build index maps: originalId -> list position
        val gameIndex = mutableMapOf<Long, Int>()
        allGames.forEachIndexed { i, gwr -> gameIndex[gwr.game.id] = i }
        val collectionIndex = mutableMapOf<Long, Int>()
        allCollections.forEachIndexed { i, c -> collectionIndex[c.id] = i }
        val tagIndex = mutableMapOf<Long, Int>()
        allTags.forEachIndexed { i, t -> tagIndex[t.id] = i }

        val backupData = BackupData(
            games = allGames.map { gwr ->
                BackupGame(
                    title = gwr.game.title,
                    inLibrary = gwr.game.inLibrary,
                    coverUrl = gwr.game.coverUrl,
                    description = gwr.game.description,
                    developer = gwr.game.developer,
                    engine = gwr.game.engine,
                    version = gwr.game.version,
                    status = gwr.game.status,
                    personalRating = gwr.game.personalRating,
                    f95Rating = gwr.game.f95Rating,
                    dateAdded = gwr.game.dateAdded,
                    lastPlayed = gwr.game.lastPlayed,
                    playTimeMinutes = gwr.game.playTimeMinutes,
                    notes = gwr.game.notes,
                    f95Url = gwr.game.f95Url,
                    sourceType = gwr.game.sourceType,
                    sourceUrl = gwr.game.sourceUrl,
                    changelog = gwr.game.changelog,
                    devLinks = gwr.game.devLinks
                        ?.let { parseDevLinks(it) }
                        ?: emptyList(),
                    downloadLinks = gwr.game.downloadLinks
                        ?.let { parseDownloadLinks(it) }
                        ?: emptyList(),
                )
            },
            collections = allCollections.map { entity ->
                BackupCollection(
                    name = entity.name,
                    description = entity.description,
                    color = entity.color,
                    order = entity.order,
                )
            },
            gameCollections = allGameCollections.map { ref ->
                BackupGameCollection(
                    gameIndex = gameIndex[ref.gameId] ?: 0,
                    collectionIndex = collectionIndex[ref.collectionId] ?: 0,
                )
            },
            playSessions = allSessions.map { entity ->
                BackupPlaySession(
                    gameIndex = gameIndex[entity.gameId] ?: 0,
                    routeIndex = entity.routeId?.let { null },
                    startTime = entity.startTime,
                    endTime = entity.endTime,
                    durationMinutes = entity.durationMinutes,
                    notes = entity.notes,
                )
            },
            routes = allRoutes.map { entity ->
                BackupRoute(
                    gameIndex = gameIndex[entity.gameId] ?: 0,
                    name = entity.name,
                    progress = entity.progress,
                    status = entity.status,
                    order = entity.order,
                    notes = entity.notes,
                )
            },
            tags = allTags.map { entity ->
                BackupTag(name = entity.name)
            },
            gameTags = allGameTags.map { ref ->
                BackupGameTag(
                    gameIndex = gameIndex[ref.gameId] ?: 0,
                    tagIndex = tagIndex[ref.tagId] ?: 0,
                )
            },
        )
        return gson.toJson(backupData)
    }

    suspend fun exportToFile(context: Context, uri: Uri) {
        val json = exportToJson()
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(json.toByteArray(Charsets.UTF_8))
        }
    }

    suspend fun importFromJson(json: String): ImportResult {
        return try {
            val backupData = gson.fromJson(json, BackupData::class.java)
                ?: return ImportResult(false, "Invalid backup file", 0, 0)

            if (backupData.version < 1 || backupData.version > 1) {
                return ImportResult(false, "Unsupported backup version", 0, 0)
            }

            // Clear cross-refs first (FK constraints)
            gameTagDao.deleteAll()
            gameCollectionDao.deleteAll()

            // Import games — store list-position → newId mapping
            val newGameIds = mutableListOf<Long>()
            for (bg in backupData.games) {
                val id = gameDao.insertGame(
                    GameEntity(
                        title = bg.title,
                        inLibrary = bg.inLibrary,
                        coverUrl = bg.coverUrl,
                        description = bg.description,
                        developer = bg.developer,
                        engine = bg.engine,
                        version = bg.version,
                        status = bg.status,
                        personalRating = bg.personalRating,
                        f95Rating = bg.f95Rating,
                        dateAdded = bg.dateAdded,
                        lastPlayed = bg.lastPlayed,
                        playTimeMinutes = bg.playTimeMinutes,
                        notes = bg.notes,
                        f95Url = bg.f95Url,
                        sourceType = bg.sourceType,
                        sourceUrl = bg.sourceUrl,
                        changelog = bg.changelog,
                        devLinks = bg.devLinks.orEmpty().takeIf { it.isNotEmpty() }?.let { gson.toJson(it) },
                        downloadLinks = bg.downloadLinks.orEmpty().takeIf { it.isNotEmpty() }?.let { gson.toJson(it) },
                    )
                )
                newGameIds.add(id)
            }

            // Import collections
            val newCollectionIds = mutableListOf<Long>()
            for (bc in backupData.collections) {
                val id = collectionDao.insertCollection(
                    CollectionEntity(
                        name = bc.name,
                        description = bc.description,
                        color = bc.color,
                        order = bc.order,
                    )
                )
                newCollectionIds.add(id)
            }

            // Import routes
            for (br in backupData.routes) {
                val gameId = newGameIds[br.gameIndex] ?: continue
                routeDao.insertRoute(
                    GameRouteEntity(
                        gameId = gameId,
                        name = br.name,
                        progress = br.progress,
                        status = br.status,
                        order = br.order,
                        notes = br.notes,
                    )
                )
            }

            // Import play sessions
            for (bps in backupData.playSessions) {
                val gameId = newGameIds[bps.gameIndex] ?: continue
                sessionDao.insertSession(
                    PlaySessionEntity(
                        gameId = gameId,
                        startTime = bps.startTime,
                        endTime = bps.endTime,
                        durationMinutes = bps.durationMinutes,
                        notes = bps.notes,
                    )
                )
            }

            // Import tags
            val newTagIds = mutableListOf<Long>()
            for (bt in backupData.tags) {
                val id = tagDao.insertTag(TagEntity(name = bt.name))
                newTagIds.add(id)
            }

            // Import game-collection cross-refs
            for (bgc in backupData.gameCollections) {
                val gameId = newGameIds[bgc.gameIndex] ?: continue
                val collectionId = newCollectionIds[bgc.collectionIndex] ?: continue
                gameCollectionDao.insert(GameCollectionCrossRef(gameId, collectionId))
            }

            // Import game-tag cross-refs
            for (bgt in backupData.gameTags) {
                val gameId = newGameIds[bgt.gameIndex] ?: continue
                val tagId = newTagIds[bgt.tagIndex] ?: continue
                gameTagDao.insert(GameTagCrossRef(gameId, tagId))
            }

            ImportResult(
                success = true,
                message = "Import complete",
                gamesImported = backupData.games.size,
                collectionsImported = backupData.collections.size,
            )
        } catch (e: Exception) {
            ImportResult(false, "Import failed: ${e.message}", 0, 0)
        }
    }

    suspend fun importFromFile(context: Context, uri: Uri): ImportResult {
        return try {
            val reader = BufferedReader(
                InputStreamReader(context.contentResolver.openInputStream(uri))
            )
            val json = reader.readText()
            reader.close()
            importFromJson(json)
        } catch (e: Exception) {
            ImportResult(false, "Import failed: ${e.message}", 0, 0)
        }
    }

    private fun parseDevLinks(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<String>>(raw, object : TypeToken<List<String>>() {}.type)
        }.getOrElse { emptyList() }
    }

    private fun parseDownloadLinks(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<String>>(raw, object : TypeToken<List<String>>() {}.type)
        }.getOrElse { emptyList() }
    }
}
