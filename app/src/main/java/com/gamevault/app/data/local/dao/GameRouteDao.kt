package com.gamevault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gamevault.app.data.local.entity.GameRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameRouteDao {

    @Query("SELECT * FROM game_routes WHERE game_id = :gameId ORDER BY `order` ASC")
    fun getRoutesForGameFlow(gameId: Long): Flow<List<GameRouteEntity>>

    @Query("SELECT * FROM game_routes WHERE game_id = :gameId ORDER BY `order` ASC")
    suspend fun getRoutesForGame(gameId: Long): List<GameRouteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: GameRouteEntity): Long

    @Update
    suspend fun updateRoute(route: GameRouteEntity)

    @Delete
    suspend fun deleteRoute(route: GameRouteEntity)

    @Query("DELETE FROM game_routes WHERE game_id = :gameId")
    suspend fun deleteRoutesForGame(gameId: Long)
}
