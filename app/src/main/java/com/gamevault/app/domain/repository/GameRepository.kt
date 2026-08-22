package com.gamevault.app.domain.repository

import com.gamevault.app.domain.model.Collection
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameRoute
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.domain.model.PlaySession
import com.gamevault.app.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/**
 * Outcome of a library refresh pass.
 *
 * Every in-library game counts toward [checked]; games whose scraped fields
 * actually changed count toward [updated]; scrape failures count toward
 * [errors]. Sources without a scraper are checked but never updated.
 */
data class LibraryRefreshResult(
    val updated: Int,
    val checked: Int,
    val errors: Int,
)

/**
 * Single source of truth for game library data.
 * Domain layer interface — implementation lives in data layer.
 */
interface GameRepository {

    // ── Games ──────────────────────────────────────────────

    fun observeAllGames(): Flow<List<Game>>
    fun observeGameById(gameId: Long): Flow<Game?>
    fun searchGames(query: String): Flow<List<Game>>
    fun observeGamesByStatus(status: GameStatus): Flow<List<Game>>

    suspend fun getAllGames(): List<Game>
    suspend fun getGameById(gameId: Long): Game?
    suspend fun getGameBySourceUrl(url: String): Game?
    suspend fun getGameByF95Url(url: String): Game?
    suspend fun saveGame(game: Game): Long
    suspend fun updateGame(game: Game)
    suspend fun refreshSavedGames(): LibraryRefreshResult
    suspend fun updateGamePlayTime(gameId: Long, minutes: Long)
    suspend fun setGameInLibrary(gameId: Long, inLibrary: Boolean)
    suspend fun setGameLocalCover(gameId: Long, path: String?)
    suspend fun deleteGame(gameId: Long)
    suspend fun getGameCount(): Int
    fun observeUpdateAvailableCount(): Flow<Int>
    suspend fun clearUpdateAvailable(gameId: Long)

    // ── Routes ─────────────────────────────────────────────

    fun observeRoutesForGame(gameId: Long): Flow<List<GameRoute>>
    fun observeAllRoutes(): Flow<List<GameRoute>>
    suspend fun getRoutesForGame(gameId: Long): List<GameRoute>
    suspend fun saveRoute(route: GameRoute): Long
    suspend fun updateRoute(route: GameRoute)
    suspend fun deleteRoute(route: GameRoute)

    // ── Play Sessions ──────────────────────────────────────

    fun observeSessionsForGame(gameId: Long): Flow<List<PlaySession>>
    suspend fun getSessionsForGame(gameId: Long): List<PlaySession>
    suspend fun getTotalPlayTime(gameId: Long): Long
    suspend fun saveSession(session: PlaySession): Long
    suspend fun updateSession(session: PlaySession)

    // ── Tags ───────────────────────────────────────────────

    fun observeAllTags(): Flow<List<Tag>>
    suspend fun getAllTags(): List<Tag>
    suspend fun createTag(name: String): Tag
    suspend fun deleteTag(tagId: Long)

    // ── Collections ────────────────────────────────────────

    suspend fun updateGameStatusBulk(gameIds: List<Long>, status: GameStatus)
    suspend fun addGamesToCollection(gameIds: List<Long>, collectionId: Long)
    suspend fun deleteGames(gameIds: List<Long>)

    fun observeAllCollections(): Flow<List<Collection>>
    suspend fun getAllCollections(): List<Collection>
    suspend fun saveCollection(collection: Collection): Long
    suspend fun updateCollection(collection: Collection)
    suspend fun deleteCollection(collection: Collection)

    // ── Collection Membership ──────────────────────────────

    suspend fun addGameToCollection(gameId: Long, collectionId: Long)
    suspend fun removeGameFromCollection(gameId: Long, collectionId: Long)
    suspend fun getCollectionIdsForGame(gameId: Long): List<Long>
    fun observeGamesInCollection(collectionId: Long): Flow<List<Game>>
    suspend fun getGameCountForCollection(collectionId: Long): Int

    fun observeGameCollections(gameId: Long): Flow<List<Collection>>

    fun observeAllSessions(): Flow<List<PlaySession>>
    suspend fun getAllPlaySessions(): List<PlaySession>
}
