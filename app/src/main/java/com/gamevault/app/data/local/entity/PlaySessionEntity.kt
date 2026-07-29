package com.gamevault.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gamevault.app.domain.model.PlaySession

@Entity(
    tableName = "play_sessions",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["game_id"])],
)
data class PlaySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "game_id")
    val gameId: Long,

    @ColumnInfo(name = "route_id")
    val routeId: Long? = null,

    @ColumnInfo(name = "start_time")
    val startTime: Long,

    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,

    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Long? = null,

    val notes: String? = null,
)

fun PlaySessionEntity.toDomainModel(): PlaySession = PlaySession(
    id = id,
    gameId = gameId,
    routeId = routeId,
    startTime = startTime,
    endTime = endTime,
    durationMinutes = durationMinutes,
    notes = notes,
)

fun PlaySession.toEntity(): PlaySessionEntity = PlaySessionEntity(
    id = id,
    gameId = gameId,
    routeId = routeId,
    startTime = startTime,
    endTime = endTime,
    durationMinutes = durationMinutes,
    notes = notes,
)
