package com.gamevault.app.data.repository

import com.gamevault.app.data.local.dao.CollectionDao
import com.gamevault.app.data.local.dao.GameCollectionDao
import com.gamevault.app.data.local.dao.GameDao
import com.gamevault.app.data.local.dao.GameRouteDao
import com.gamevault.app.data.local.dao.GameTagDao
import com.gamevault.app.data.local.dao.PlaySessionDao
import com.gamevault.app.data.local.dao.TagDao
import com.gamevault.app.data.local.entity.GameCollectionCrossRef
import com.gamevault.app.data.local.entity.GameRouteEntity
import com.gamevault.app.data.local.entity.GameTagCrossRef
import com.gamevault.app.data.local.entity.PlaySessionEntity
import com.gamevault.app.data.local.entity.TagEntity
import com.gamevault.app.data.local.entity.toDomainModel
import com.gamevault.app.data.local.entity.toEntity
import com.gamevault.app.data.remote.F95ZoneScraper
import com.gamevault.app.data.remote.FapForFunScraper
import com.gamevault.app.data.remote.ItchScraper
import com.gamevault.app.data.remote.RyuugamesScraper
import com.gamevault.app.data.remote.ScrapeResult
import com.gamevault.app.data.settings.AppSettings
import com.gamevault.app.domain.model.Collection
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameEngine
import com.gamevault.app.domain.model.GameRoute
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.domain.model.PlaySession
import com.gamevault.app.domain.model.SourceType
import com.gamevault.app.domain.model.Tag
import com.gamevault.app.domain.repository.GameRepository
import com.gamevault.app.domain.repository.LibraryRefreshResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.URI

/** Cap on concurrent scrapes during a refresh pass (same wiring as the sources). */
private const val MAX_REFRESH_CONCURRENCY = 3

/** Per-game outcome of a library refresh pass. */
private enum class RefreshOutcome { UPDATED, UNCHANGED, ERROR }

class GameRepositoryImpl(
    private val gameDao: GameDao,
    private val routeDao: GameRouteDao,
    private val sessionDao: PlaySessionDao,
    private val tagDao: TagDao,
    private val collectionDao: CollectionDao,
    private val gameCollectionDao: GameCollectionDao,
    private val gameTagDao: GameTagDao,
    private val f95ZoneScraper: F95ZoneScraper,
    private val ryuugamesScraper: RyuugamesScraper,
    private val itchScraper: ItchScraper,
    private val fapForFunScraper: FapForFunScraper,
    private val appSettings: AppSettings,
) : GameRepository {

    /** Cap on concurrent scrapes during a refresh pass (same wiring as the sources). */
    private val refreshDispatcher = Dispatchers.IO.limitedParallelism(MAX_REFRESH_CONCURRENCY)

    // ── Games ──────────────────────────────────────────────

    override fun observeAllGames(): Flow<List<Game>> =
        gameDao.getAllGamesFlow().map { list -> list.map { it.toDomainModel() } }

    override fun observeGameById(gameId: Long): Flow<Game?> =
        gameDao.getGameByIdFlow(gameId).map { it?.toDomainModel() }

    override fun searchGames(query: String): Flow<List<Game>> =
        gameDao.searchGamesFlow(query).map { list -> list.map { it.toDomainModel() } }

    override fun observeGamesByStatus(status: GameStatus): Flow<List<Game>> =
        gameDao.getGamesByStatusFlow(status.name).map { list -> list.map { it.toDomainModel() } }

    override suspend fun getAllGames(): List<Game> =
        gameDao.getAllGames().map { it.toDomainModel() }

    override suspend fun getGameById(gameId: Long): Game? =
        gameDao.getGameById(gameId)?.toDomainModel()

    override suspend fun getGameBySourceUrl(url: String): Game? =
        gameDao.getGameBySourceUrl(url)?.toDomainModel()

    override suspend fun saveGame(game: Game): Long {
        val gameId = gameDao.insertGame(game.toEntity())

        // Save routes
        game.routes.forEach { route ->
            routeDao.insertRoute(route.copy(gameId = gameId).toEntity())
        }

        // Save tags and link them to the game via cross-ref. insertTag uses
        // IGNORE, so an existing tag is a no-op — the name lookup resolves the
        // authoritative id in both cases (inserted or pre-existing).
        game.tags.forEach { tag ->
            tagDao.insertTag(tag.toEntity())
            tagDao.getTagByName(tag.name)?.let { found ->
                gameTagDao.insert(GameTagCrossRef(gameId = gameId, tagId = found.id))
            }
        }

        return gameId
    }

    override suspend fun updateGame(game: Game) {
        gameDao.updateGame(game.toEntity())
    }

    override suspend fun refreshSavedGames(): LibraryRefreshResult {
        return try {
            val games = getAllGames().filter { it.inLibrary }
            val cookie = appSettings.f95zoneCookie.first()
            val outcomes = coroutineScope {
                games.map { game ->
                    async(refreshDispatcher) {
                        try {
                            refreshSingleGame(game, cookie)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            RefreshOutcome.ERROR
                        }
                    }
                }.awaitAll()
            }
            LibraryRefreshResult(
                updated = outcomes.count { it == RefreshOutcome.UPDATED },
                checked = games.size,
                errors = outcomes.count { it == RefreshOutcome.ERROR },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never crash the pass: surface as an error result instead.
            LibraryRefreshResult(updated = 0, checked = 0, errors = 1)
        }
    }

    /**
     * Scrape fresh metadata for one saved game and persist it only when the
     * scraped fields actually changed. User-owned fields are never touched.
     */
    private suspend fun refreshSingleGame(stored: Game, cookie: String?): RefreshOutcome {
        // Re-read the current row: the snapshot taken at pass start may already
        // be stale (user edits mid-pass), and the game may have been deleted
        // while the pass was running. Merge onto the freshest data and skip
        // the write when the row is gone.
        val current = getGameById(stored.id) ?: return RefreshOutcome.UNCHANGED
        val result = when (current.sourceType) {
            SourceType.F95ZONE -> current.f95Url?.let {
                f95ZoneScraper.scrapeGame(it, cookieForF95Host(it, cookie))
            }
            SourceType.RYUU_GAMES -> current.sourceUrl?.let { ryuugamesScraper.scrapeGame(it) }
            SourceType.ITCHIO -> current.sourceUrl?.let { itchScraper.scrapeGame(it) }
            SourceType.FAP_FOR_FUN -> current.sourceUrl?.let { fapForFunScraper.scrapeGame(it) }
            // Steam / VNDB / DLSite / Manual have no scraper: checked, no update.
            else -> null
        }
        return when (result) {
            is ScrapeResult.Success -> {
                val merged = mergeScrapedFields(current, result.game)
                if (merged == current) {
                    RefreshOutcome.UNCHANGED
                } else {
                    gameDao.updateGame(merged.toEntity())
                    RefreshOutcome.UPDATED
                }
            }
            is ScrapeResult.Error -> RefreshOutcome.ERROR
            null -> RefreshOutcome.UNCHANGED
        }
    }

    /**
     * The F95Zone session cookie is bound to f95zone.to — never send it to a
     * foreign host (an imported or stale link can point elsewhere).
     */
    private fun cookieForF95Host(url: String?, cookie: String?): String? {
        val host = url?.let { runCatching { URI(it).host }.getOrNull() }
        return if (host == null || host.endsWith("f95zone.to")) cookie else null
    }

    /**
     * True when a scrape answered with a bot-check shell or redirect page
     * instead of real content (e.g. "Just a moment..." from Cloudflare).
     */
    private fun isShellTitle(title: String?): Boolean {
        if (title.isNullOrBlank()) return true
        val normalized = title.trim().lowercase()
        if (normalized.startsWith("just a moment")) return true
        return normalized == "attention required" ||
            normalized == "access denied" ||
            normalized == "f95zone"
    }

    /**
     * Merge a fresh scrape onto the stored game: scraped fields win when the
     * scrape produced a value, otherwise the stored value is kept (a scraper
     * that failed to extract a field must not wipe existing data). Everything
     * the user owns — id, cover, rating, status, notes, playtime, dates,
     * library flag, relations — stays from [stored]. A shell page has no real
     * data, so the stored game is returned untouched.
     */
    private fun mergeScrapedFields(stored: Game, scraped: Game): Game {
        if (isShellTitle(scraped.title)) return stored
        return stored.copy(
            title = scraped.title,
            coverUrl = scraped.coverUrl ?: stored.coverUrl,
            description = scraped.description ?: stored.description,
            developer = scraped.developer ?: stored.developer,
            publisher = scraped.publisher ?: stored.publisher,
            engine = if (scraped.engine != null && scraped.engine != GameEngine.OTHER) {
                scraped.engine
            } else {
                stored.engine
            },
            version = scraped.version ?: stored.version,
            changelog = scraped.changelog ?: stored.changelog,
            f95Rating = scraped.f95Rating ?: stored.f95Rating,
            devLinks = scraped.devLinks.takeIf { it.isNotEmpty() } ?: stored.devLinks,
            downloadLinks = scraped.downloadLinks.takeIf { it.isNotEmpty() } ?: stored.downloadLinks,
            f95Url = scraped.f95Url ?: stored.f95Url,
            sourceUrl = scraped.sourceUrl ?: stored.sourceUrl,
            sourceType = scraped.sourceType,
        )
    }

    override suspend fun updateGamePlayTime(gameId: Long, minutes: Long) {
        gameDao.updatePlayTimeMinutes(gameId, minutes)
    }

    override suspend fun setGameInLibrary(gameId: Long, inLibrary: Boolean) {
        gameDao.updateGameLibraryState(gameId, inLibrary)
    }

    override suspend fun setGameLocalCover(gameId: Long, path: String?) {
        gameDao.updateGameLocalCover(gameId, path)
    }

    override suspend fun deleteGame(gameId: Long) {
        gameDao.deleteGameById(gameId)
    }

    override suspend fun getGameCount(): Int = gameDao.getGameCount()

    override suspend fun updateGameStatusBulk(gameIds: List<Long>, status: GameStatus) {
        gameDao.updateGameStatusBulk(gameIds, status.name)
    }

    override suspend fun addGamesToCollection(gameIds: List<Long>, collectionId: Long) {
        val crossRefs = gameIds.map { GameCollectionCrossRef(it, collectionId) }
        gameCollectionDao.insertBulk(crossRefs)
    }

    override suspend fun deleteGames(gameIds: List<Long>) {
        gameDao.deleteGamesBulk(gameIds)
    }

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

    override suspend fun updateCollection(collection: Collection) =
        collectionDao.updateCollection(collection.toEntity())

    override suspend fun deleteCollection(collection: Collection) =
        collectionDao.deleteCollection(collection.toEntity())

    // ── Collection Membership ──────────────────────────────

    override suspend fun addGameToCollection(gameId: Long, collectionId: Long) {
        gameCollectionDao.insert(GameCollectionCrossRef(gameId, collectionId))
    }

    override suspend fun removeGameFromCollection(gameId: Long, collectionId: Long) {
        gameCollectionDao.deleteByGameAndCollection(gameId, collectionId)
    }

    override suspend fun getCollectionIdsForGame(gameId: Long): List<Long> =
        gameCollectionDao.getCollectionIdsForGame(gameId)

    override fun observeGamesInCollection(collectionId: Long): Flow<List<Game>> =
        gameDao.getGamesInCollectionFlow(collectionId).map { list -> list.map { it.toDomainModel() } }

    override suspend fun getGameCountForCollection(collectionId: Long): Int =
        gameCollectionDao.getGameCountForCollection(collectionId)

    override fun observeGameCollections(gameId: Long): Flow<List<Collection>> =
        gameCollectionDao.getCollectionsForGameFlow(gameId).map { list -> list.map { it.toDomainModel() } }

    override fun observeAllSessions(): Flow<List<PlaySession>> =
        sessionDao.getAllSessionsFlow().map { list -> list.map { it.toDomainModel() } }

    override suspend fun getAllPlaySessions(): List<PlaySession> =
        sessionDao.getAllSessions().map { it.toDomainModel() }
}
