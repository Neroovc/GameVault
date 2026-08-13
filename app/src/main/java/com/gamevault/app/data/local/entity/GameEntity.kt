package com.gamevault.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameEngine
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.domain.model.SourceType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(
    tableName = "games",
    indices = [
        Index(value = ["title"]),
        Index(value = ["status"]),
        Index(value = ["date_added"]),
        Index(value = ["f95_url"], unique = true),
    ],
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    @ColumnInfo(name = "cover_url")
    val coverUrl: String? = null,

    @ColumnInfo(name = "local_cover_path")
    val localCoverPath: String? = null,

    val description: String? = null,
    val developer: String? = null,
    val publisher: String? = null,

    val engine: String? = null,          // stored as GameEngine.name
    val version: String? = null,
    val status: String = GameStatus.NOT_STARTED.name,

    @ColumnInfo(name = "personal_rating")
    val personalRating: Float? = null,

    @ColumnInfo(name = "f95_rating")
    val f95Rating: Float? = null,

    @ColumnInfo(name = "date_added")
    val dateAdded: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_played")
    val lastPlayed: Long? = null,

    @ColumnInfo(name = "play_time_minutes")
    val playTimeMinutes: Long = 0,

    @ColumnInfo(name = "in_library")
    val inLibrary: Boolean = true,

    val notes: String? = null,

    @ColumnInfo(name = "f95_url")
    val f95Url: String? = null,

    @ColumnInfo(name = "source_type")
    val sourceType: String = SourceType.MANUAL.name,

    @ColumnInfo(name = "source_url")
    val sourceUrl: String? = null,

    val changelog: String? = null,

    @ColumnInfo(name = "dev_links")
    val devLinks: String? = null,        // JSON-serialized List<String>, null = none

    @ColumnInfo(name = "download_links")
    val downloadLinks: String? = null,   // JSON-serialized List<String>, null = none
)

private val devLinksJsonType = object : TypeToken<List<String>>() {}.type
private val downloadLinksJsonType = object : TypeToken<List<String>>() {}.type

private fun parseDevLinks(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        Gson().fromJson<List<String>>(raw, devLinksJsonType)
    }.getOrElse { emptyList() }
}

private fun parseDownloadLinks(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        Gson().fromJson<List<String>>(raw, downloadLinksJsonType)
    }.getOrElse { emptyList() }
}

private fun serializeDevLinks(links: List<String>): String? {
    if (links.isEmpty()) return null
    return runCatching { Gson().toJson(links) }.getOrNull()
}

private fun serializeDownloadLinks(links: List<String>): String? {
    if (links.isEmpty()) return null
    return runCatching { Gson().toJson(links) }.getOrNull()
}

fun GameEntity.toDomainModel(): Game = Game(
    id = id,
    title = title,
    coverUrl = coverUrl,
    localCoverPath = localCoverPath,
    description = description,
    developer = developer,
    publisher = publisher,
    engine = engine?.let { runCatching { GameEngine.valueOf(it) }.getOrNull() },
    version = version,
    status = runCatching { GameStatus.valueOf(status) }.getOrElse { GameStatus.NOT_STARTED },
    personalRating = personalRating,
    f95Rating = f95Rating,
    dateAdded = dateAdded,
    lastPlayed = lastPlayed,
    playTimeMinutes = playTimeMinutes,
    inLibrary = inLibrary,
    notes = notes,
    f95Url = f95Url,
    sourceType = runCatching { SourceType.valueOf(sourceType) }.getOrElse { SourceType.MANUAL },
    sourceUrl = sourceUrl,
    changelog = changelog,
    devLinks = parseDevLinks(devLinks),
    downloadLinks = parseDownloadLinks(downloadLinks),
)

fun Game.toEntity(): GameEntity = GameEntity(
    id = id,
    title = title,
    coverUrl = coverUrl,
    localCoverPath = localCoverPath,
    description = description,
    developer = developer,
    publisher = publisher,
    engine = engine?.name,
    version = version,
    status = status.name,
    personalRating = personalRating,
    f95Rating = f95Rating,
    dateAdded = dateAdded,
    lastPlayed = lastPlayed,
    playTimeMinutes = playTimeMinutes,
    inLibrary = inLibrary,
    notes = notes,
    f95Url = f95Url,
    sourceType = sourceType.name,
    sourceUrl = sourceUrl,
    changelog = changelog,
    devLinks = serializeDevLinks(devLinks),
    downloadLinks = serializeDownloadLinks(downloadLinks),
)
