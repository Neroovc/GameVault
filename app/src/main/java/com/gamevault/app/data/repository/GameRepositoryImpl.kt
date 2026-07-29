package com.gamevault.app.data.repository

import com.gamevault.app.data.local.dao.CollectionDao
import com.gamevault.app.data.local.dao.GameDao
import com.gamevault.app.data.local.dao.GameRouteDao
import com.gamevault.app.data.local.dao.PlaySessionDao
import com.gamevault.app.data.local.dao.TagDao
import com.gamevault.app.data.local.entity.GameRouteEntity
import com.gamevault.app.data.local.entity.GameTagCrossRef
import com.gamevault.app.data.local.entity.PlaySessionEntity
import com.gamevault.app.data.local.entity.TagEntity
import com.gamevault.app.data.local.entity.toDomainModel
import com.gamevault.app.data.local.entity.toEntity
import com.gamevault.app.domain.model.Collection
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameRoute
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.domain.model.PlaySession
import com.gamevault.app.domain.model.Tag
import com.gamevault.app.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GameRepositoryImpl(
    private val gameDao: GameDao,
    private val routeDao: GameRouteDao,
    private val sessionDao: PlaySessionDao,
    private val tagDao: TagDao,
    private val collectionDao: CollectionDao,
) : GameRepository {

    // ── Games ──────────────────────────────────────────────

    override fun observeAllGames(): Flow<List<Game>> =
        gameDao.getAllGamesFlow().map { list -> list.map { it.toDomainModel() } }

    override fun observeGameById(gameId: Long): Flow<Game?> =
        gameDao.getGameByIdFlow(gameId).map { it?.toDomainModel() }

    override fun searchGames(query: String): Flow<List<Game>> =
        gameDao.searchGamesFlow(query).map { list -> list.map { it.toDomainModel() } }

    override fun observeGamesByStatus(status: GameStatus): Flow<List<Game>> =
        gameDao.getGamesByStatusFlow(status.name).map { list -> list.map { it.toDomainModel() } }

    override suspend fun getGameById(gameId: Long): Game? =
        gameDao.getGameById(gameId)?.toDomainModel()

    override suspend fun saveGame(game: Game): Long {
        val gameId = gameDao.insertGame(game.toEntity())

        // Save routes
        game.routes.forEach { route ->
            routeDao.insertRoute(route.copy(gameId = gameId).toEntity())
        }

        // Save tags
        game.tags.forEach { tag ->
            val tagId = tagDao.insertTag(tag.toEntity())
            // Link tag to game via cross-ref is handled if needed
        }

        return gameId
    }

    override suspend fun updateGame(game: Game) {
        gameDao.updateGame(game.toEntity())
    }

    override suspend fun deleteGame(gameId: Long) {
        gameDao.deleteGameById(gameId)
    }

    override suspend fun getGameCount(): Int = gameDao.getGameCount()

    // ── Routes ─────────────────────────────────────────────

    override fun observeRoutesForGame(gameId: Long): Flow<List<GameRoute>> =
        routeDao.getRoutesForGameFlow(gameId).map { list -> list.map { it.toDomainModel() } }

    override suspend fun getRoutesForGame(gameId: Long): List<GameRoute> =
        routeDao.getRoutesForGame(gameId).map { it.toDomainModel() }

    override suspend fun saveRoute(route: GameRoute): Long =
        routeDao.insertRoute(route.toEntity())

    override suspend fun updateRoute(route: GameRoute) =
        routeDao.updateRoute(route.toEntity())

    override suspend fun deleteRoute(route: GameRoute) =
        routeDao.deleteRoute(route.toEntity())

    // ── Play Sessions ──────────────────────────────────────

    override fun observeSessionsForGame(gameId: Long): Flow<List<PlaySession>> =
        sessionDao.getSessionsForGameFlow(gameId).map { list -> list.map { it.toDomainModel() } }

    override suspend fun getSessionsForGame(gameId: Long): List<PlaySession> =
        sessionDao.getSessionsForGame(gameId).map { it.toDomainModel() }

    override suspend fun getTotalPlayTime(gameId: Long): Long =
        sessionDao.getTotalPlayTime(gameId)

    override suspend fun saveSession(session: PlaySession): Long =
        sessionDao.insertSession(session.toEntity())

    override suspend fun updateSession(session: PlaySession) =
        sessionDao.updateSession(session.toEntity())

    // ── Tags ───────────────────────────────────────────────

    override fun observeAllTags(): Flow<List<Tag>> =
        tagDao.getAllTagsFlow().map { list -> list.map { it.toDomainModel() } }

    override suspend fun getAllTags(): List<Tag> =
        tagDao.getAllTags().map { it.toDomainModel() }

    override suspend fun createTag(name: String): Tag {
        val id = tagDao.insertTag(TagEntity(name = name))
        return Tag(id = id, name = name)
    }

    override suspend fun deleteTag(tagId: Long) =
        tagDao.deleteTag(tagId)

    // ── Collections ────────────────────────────────────────

    override fun observeAllCollections(): Flow<List<Collection>> =
        collectionDao.getAllCollectionsFlow().map { list -> list.map { it.toDomainModel() } }

    override suspend fun getAllCollections(): List<Collection> =
        collectionDao.getAllCollections().map { it.toDomainModel() }

    override suspend fun saveCollection(collection: Collection): Long =
        collectionDao.insertCollection(collection.toEntity())

    override suspend fun deleteCollection(collection: Collection) =
        collectionDao.deleteCollection(collection.toEntity())
}
