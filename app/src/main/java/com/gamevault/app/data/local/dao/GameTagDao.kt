package com.gamevault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gamevault.app.data.local.entity.GameTagCrossRef

@Dao
interface GameTagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(crossRef: GameTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBulk(crossRefs: List<GameTagCrossRef>)

    @Query("SELECT * FROM game_tag_cross_ref")
    suspend fun getAll(): List<GameTagCrossRef>

    @Query("DELETE FROM game_tag_cross_ref WHERE game_id = :gameId AND tag_id = :tagId")
    suspend fun deleteByGameAndTag(gameId: Long, tagId: Long)

    @Query("DELETE FROM game_tag_cross_ref WHERE game_id = :gameId")
    suspend fun deleteAllForGame(gameId: Long)

    @Query("DELETE FROM game_tag_cross_ref")
    suspend fun deleteAll()
}
