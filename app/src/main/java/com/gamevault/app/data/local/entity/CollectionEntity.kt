package com.gamevault.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gamevault.app.domain.model.Collection

@Entity(
    tableName = "collections",
    indices = [Index(value = ["name"], unique = true)],
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val color: Long? = null,
    val order: Int = 0,
)

fun CollectionEntity.toDomainModel(): Collection = Collection(
    id = id, name = name, description = description,
    color = color, order = order,
)
fun Collection.toEntity(): CollectionEntity = CollectionEntity(
    id = id, name = name, description = description,
    color = color, order = order,
)
