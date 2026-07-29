package com.gamevault.app

import android.app.Application
import com.gamevault.app.data.local.GameVaultDatabase
import com.gamevault.app.data.repository.GameRepositoryImpl
import com.gamevault.app.domain.repository.GameRepository

/**
 * Application class — holds the DI container.
 * No Hilt/Koin, just clean manual dependency injection.
 */
class GameVaultApp : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}

/**
 * Manual DI container. Single source of truth for dependencies.
 */
class AppContainer(private val app: GameVaultApp) {

    private val database: GameVaultDatabase by lazy {
        GameVaultDatabase.create(app)
    }

    val gameRepository: GameRepository by lazy {
        GameRepositoryImpl(
            gameDao = database.gameDao(),
            routeDao = database.gameRouteDao(),
            sessionDao = database.playSessionDao(),
            tagDao = database.tagDao(),
            collectionDao = database.collectionDao(),
        )
    }
}
