package com.gamevault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gamevault.app.data.local.entity.CollectionEntity
import com.gamevault.app.data.local.entity.GameCollectionCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface GameCollectionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(crossRef: GameCollectionCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBulk(crossRefs: List<GameCollectionCrossRef>)

    @Delete
    suspend fun delete(crossRef: GameCollectionCrossRef)

    @Query("DELETE FROM game_collection_cross_ref WHERE game_id = :gameId AND collection_id = :collectionId")
    suspend fun deleteByGameAndCollection(gameId: Long, collectionId: Long)

    @Query("DELETE FROM game_collection_cross_ref WHERE game_id = :gameId")
    suspend fun deleteAllForGame(gameId: Long)

    @Query("SELECT collection_id FROM game_collection_cross_ref WHERE game_id = :gameId")
    suspend fun getCollectionIdsForGame(gameId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM game_collection_cross_ref WHERE collection_id = :collectionId")
    suspend fun getGameCountForCollection(collectionId: Long): Int

    @Query("SELECT * FROM game_collection_cross_ref")
    suspend fun getAllCrossRefs(): List<GameCollectionCrossRef>

    @Query("""
        SELECT c.* FROM collections c
        INNER JOIN game_collection_cross_ref gcc ON c.id = gcc.collection_id
        WHERE gcc.game_id = :gameId
        ORDER BY c.`order` ASC
    """)
    fun getCollectionsForGameFlow(gameId: Long): Flow<List<CollectionEntity>>

    @Query("DELETE FROM game_collection_cross_ref")
    suspend fun deleteAll()
}
