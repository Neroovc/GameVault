package com.gamevault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gamevault.app.data.local.entity.PlaySessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaySessionDao {

    @Query("SELECT * FROM play_sessions WHERE game_id = :gameId ORDER BY start_time DESC")
    fun getSessionsForGameFlow(gameId: Long): Flow<List<PlaySessionEntity>>

    @Query("SELECT * FROM play_sessions WHERE game_id = :gameId ORDER BY start_time DESC")
    suspend fun getSessionsForGame(gameId: Long): List<PlaySessionEntity>

    @Query("""
        SELECT COALESCE(SUM(duration_minutes), 0) 
        FROM play_sessions 
        WHERE game_id = :gameId
    """)
    suspend fun getTotalPlayTime(gameId: Long): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PlaySessionEntity): Long

    @Update
    suspend fun updateSession(session: PlaySessionEntity)

    @Delete
    suspend fun deleteSession(session: PlaySessionEntity)
}
