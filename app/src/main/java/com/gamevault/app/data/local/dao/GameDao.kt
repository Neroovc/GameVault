package com.gamevault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.gamevault.app.data.local.entity.GameEntity
import com.gamevault.app.data.local.entity.GameWithRelations
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    @Transaction
    @Query("SELECT * FROM games ORDER BY date_added DESC")
    fun getAllGamesFlow(): Flow<List<GameWithRelations>>

    @Transaction
    @Query("SELECT * FROM games ORDER BY date_added DESC")
    suspend fun getAllGames(): List<GameWithRelations>

    @Transaction
    @Query("SELECT * FROM games WHERE id = :gameId")
    fun getGameByIdFlow(gameId: Long): Flow<GameWithRelations?>

    @Transaction
    @Query("SELECT * FROM games WHERE id = :gameId")
    suspend fun getGameById(gameId: Long): GameWithRelations?

    @Transaction
    @Query("SELECT * FROM games WHERE title LIKE '%' || :query || '%' ORDER BY date_added DESC")
    fun searchGamesFlow(query: String): Flow<List<GameWithRelations>>

    @Transaction
    @Query("SELECT * FROM games WHERE status = :status ORDER BY date_added DESC")
    fun getGamesByStatusFlow(status: String): Flow<List<GameWithRelations>>

    @Transaction
    @Query("""
        SELECT * FROM games
        INNER JOIN game_collection_cross_ref ON games.id = game_collection_cross_ref.game_id
        WHERE game_collection_cross_ref.collection_id = :collectionId
        ORDER BY games.date_added DESC
    """)
    fun getGamesInCollectionFlow(collectionId: Long): Flow<List<GameWithRelations>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: GameEntity): Long

    @Update
    suspend fun updateGame(game: GameEntity)

    @Delete
    suspend fun deleteGame(game: GameEntity)

    @Query("DELETE FROM games WHERE id = :gameId")
    suspend fun deleteGameById(gameId: Long)

    @Query("SELECT COUNT(*) FROM games")
    suspend fun getGameCount(): Int

    @Query("UPDATE games SET status = :status WHERE id IN (:gameIds)")
    suspend fun updateGameStatusBulk(gameIds: List<Long>, status: String)

    @Query("DELETE FROM games WHERE id IN (:gameIds)")
    suspend fun deleteGamesBulk(gameIds: List<Long>)
}
