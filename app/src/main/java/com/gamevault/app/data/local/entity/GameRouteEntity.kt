package com.gamevault.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gamevault.app.domain.model.GameRoute
import com.gamevault.app.domain.model.RouteStatus

@Entity(
    tableName = "game_routes",
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
data class GameRouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "game_id")
    val gameId: Long,

    val name: String,
    val progress: Int = 0,       // 0..100
    val status: String = RouteStatus.LOCKED.name,

    val order: Int = 0,
    val notes: String? = null,
)

fun GameRouteEntity.toDomainModel(): GameRoute = GameRoute(
    id = id,
    gameId = gameId,
    name = name,
    progress = progress,
    status = runCatching { RouteStatus.valueOf(status) }.getOrElse { RouteStatus.LOCKED },
    order = order,
    notes = notes,
)

fun GameRoute.toEntity(): GameRouteEntity = GameRouteEntity(
    id = id,
    gameId = gameId,
    name = name,
    progress = progress,
    status = status.name,
    order = order,
    notes = notes,
)
