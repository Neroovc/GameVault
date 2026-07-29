package com.gamevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gamevault.app.domain.model.Tag

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
)

fun TagEntity.toDomainModel(): Tag = Tag(id = id, name = name)
fun Tag.toEntity(): TagEntity = TagEntity(id = id, name = name)
