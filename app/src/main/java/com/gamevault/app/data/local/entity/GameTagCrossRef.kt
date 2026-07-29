package com.gamevault.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "game_tag_cross_ref",
    primaryKeys = ["game_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["game_id"]),
        Index(value = ["tag_id"]),
    ],
)
data class GameTagCrossRef(
    @ColumnInfo(name = "game_id")
    val gameId: Long,
    @ColumnInfo(name = "tag_id")
    val tagId: Long,
)
