package com.gamevault.app.ui.navigation

/**
 * Navigation route constants.
 */
object NavRoutes {
    const val LIBRARY = "library"
    const val GAME_DETAIL = "game/{gameId}"
    const val SETTINGS = "settings"
    const val SEARCH = "search"

    fun gameDetail(gameId: Long) = "game/$gameId"
}
