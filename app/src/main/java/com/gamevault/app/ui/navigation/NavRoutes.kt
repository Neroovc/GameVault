package com.gamevault.app.ui.navigation

/**
 * Navigation route constants.
 */
object NavRoutes {
    const val LIBRARY = "library"
    const val ADD_GAME = "add-game"
    const val GAME_DETAIL = "game/{gameId}"
    const val COLLECTIONS = "collections"
    const val COLLECTION_GAMES = "collection/{collectionId}"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val SEARCH = "search"
    const val SOURCE_BROWSE = "extensions/{sourceId}"

    fun gameDetail(gameId: Long) = "game/$gameId"
    fun collectionGames(collectionId: Long) = "collection/$collectionId"
    fun sourceBrowse(sourceId: String) = "extensions/$sourceId"
}
