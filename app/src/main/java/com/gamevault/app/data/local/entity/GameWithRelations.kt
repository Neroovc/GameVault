package com.gamevault.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Full game with all its relations — used by Room queries.
 */
data class GameWithRelations(
    @Embedded
    val game: GameEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "game_id",
    )
    val routes: List<GameRouteEntity> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = GameTagCrossRef::class,
            parentColumn = "game_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<TagEntity> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = GameCollectionCrossRef::class,
            parentColumn = "game_id",
            entityColumn = "collection_id",
        ),
    )
    val collections: List<CollectionEntity> = emptyList(),
)

fun GameWithRelations.toDomainModel(): com.gamevault.app.domain.model.Game {
    val domain = game.toDomainModel()
    return domain.copy(
        routes = routes.map { it.toDomainModel() },
        tags = tags.map { it.toDomainModel() },
        collections = collections.map { it.toDomainModel() },
    )
}
