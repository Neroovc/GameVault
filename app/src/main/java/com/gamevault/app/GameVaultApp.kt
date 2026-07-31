package com.gamevault.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.gamevault.app.data.local.GameVaultBackup
import com.gamevault.app.data.local.GameVaultDatabase
import com.gamevault.app.data.remote.F95ZoneScraper
import com.gamevault.app.data.remote.F95ZoneSource
import com.gamevault.app.data.remote.ItchScraper
import com.gamevault.app.data.remote.ItchSource
import com.gamevault.app.data.repository.GameRepositoryImpl
import com.gamevault.app.data.settings.AppSettings
import com.gamevault.app.domain.repository.GameRepository
import com.gamevault.app.domain.source.SourceManager
import com.gamevault.app.domain.source.SourceRegistry

/**
 * Application class — holds the DI container.
 * No Hilt/Koin, just clean manual dependency injection.
 */
class GameVaultApp : Application(), ImageLoaderFactory {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(GifDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(50 * 1024 * 1024) // 50MB
                    .build()
            }
            .build()
    }
}

/**
 * Manual DI container. Single source of truth for dependencies.
 */
class AppContainer(private val app: GameVaultApp) {

    private val database: GameVaultDatabase by lazy {
        GameVaultDatabase.create(app)
    }

    val f95ZoneScraper: F95ZoneScraper by lazy {
        F95ZoneScraper()
    }

    val f95ZoneSource: F95ZoneSource by lazy {
        F95ZoneSource(f95ZoneScraper, appSettings)
    }

    val itchScraper: ItchScraper by lazy {
        ItchScraper()
    }

    val itchSource: ItchSource by lazy {
        ItchSource(itchScraper)
    }

    val sourceRegistry: SourceRegistry by lazy {
        SourceRegistry().apply {
            register(f95ZoneSource)
            register(itchSource)
        }
    }

    val sourceManager: SourceManager by lazy {
        SourceManager(sourceRegistry, appSettings)
    }

    val appSettings: AppSettings by lazy {
        AppSettings(app)
    }

    val gameRepository: GameRepository by lazy {
        GameRepositoryImpl(
            gameDao = database.gameDao(),
            routeDao = database.gameRouteDao(),
            sessionDao = database.playSessionDao(),
            tagDao = database.tagDao(),
            collectionDao = database.collectionDao(),
            gameCollectionDao = database.gameCollectionDao(),
        )
    }

    val gameVaultBackup: GameVaultBackup by lazy {
        GameVaultBackup(
            gameDao = database.gameDao(),
            routeDao = database.gameRouteDao(),
            sessionDao = database.playSessionDao(),
            tagDao = database.tagDao(),
            collectionDao = database.collectionDao(),
            gameCollectionDao = database.gameCollectionDao(),
            gameTagDao = database.gameTagDao(),
        )
    }
}
