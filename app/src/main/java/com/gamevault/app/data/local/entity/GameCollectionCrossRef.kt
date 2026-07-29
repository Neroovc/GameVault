package com.gamevault.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "game_collection_cross_ref",
    primaryKeys = ["game_id", "collection_id"],
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["game_id"]),
        Index(value = ["collection_id"]),
    ],
)
data class GameCollectionCrossRef(
    @ColumnInfo(name = "game_id")
    val gameId: Long,
    @ColumnInfo(name = "collection_id")
    val collectionId: Long,
)
