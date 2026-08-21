package com.gamevault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gamevault.app.data.settings.ColorPalette
import com.gamevault.app.data.settings.ThemeMode
import com.gamevault.app.ui.addgame.AddGameScreen
import com.gamevault.app.ui.addgame.AddGameViewModel
import com.gamevault.app.ui.advanced.AdvancedScreen
import com.gamevault.app.ui.advanced.AdvancedViewModel
import com.gamevault.app.ui.browser.BrowserScreen
import com.gamevault.app.ui.browser.SourceBrowseScreen
import com.gamevault.app.ui.collections.CollectionsScreen
import com.gamevault.app.ui.collections.CollectionsViewModel
import com.gamevault.app.ui.datastorage.DataStorageScreen
import com.gamevault.app.ui.datastorage.DataStorageViewModel
import com.gamevault.app.ui.detail.GameDetailScreen
import com.gamevault.app.ui.detail.GameDetailViewModel
import com.gamevault.app.ui.history.HistoryScreen
import com.gamevault.app.ui.history.HistoryViewModel
import com.gamevault.app.ui.library.LibraryScreen
import com.gamevault.app.ui.library.LibraryViewModel
import com.gamevault.app.ui.more.AboutScreen
import com.gamevault.app.ui.more.MoreEntry
import com.gamevault.app.ui.more.MoreScreen
import com.gamevault.app.ui.navigation.NavRoutes
import com.gamevault.app.ui.security.SecurityScreen
import com.gamevault.app.ui.security.SecurityViewModel
import com.gamevault.app.ui.settings.AppearanceScreen
import com.gamevault.app.ui.settings.SettingsScreen
import com.gamevault.app.ui.settings.SettingsViewModel
import com.gamevault.app.ui.statistics.StatisticsScreen
import com.gamevault.app.ui.statistics.StatisticsViewModel
import com.gamevault.app.ui.theme.GameVaultTheme

private data class TabItem(
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    TabItem("Library", Icons.Default.CollectionsBookmark),
    TabItem("History", Icons.Default.History),
    TabItem("Browser", Icons.Default.Public),
    TabItem("More", Icons.Default.MoreVert),
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as GameVaultApp).appContainer

        setContent {
            val themeMode by appContainer.appSettings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val amoledDark by appContainer.appSettings.amoledDark.collectAsState(initial = false)
            val colorPalette by appContainer.appSettings.colorPalette.collectAsState(initial = ColorPalette.VIOLET)
            GameVaultTheme(
                themeMode = themeMode,
                palette = colorPalette,
                amoledDark = amoledDark,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GameVaultNavHost(
                        appContainer = appContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameVaultNavHost(
    appContainer: AppContainer,
) {
    val rootNavController = rememberNavController()

    NavHost(
        navController = rootNavController,
        startDestination = "tabs",
    ) {
        composable("tabs") {
            MainTabsScreen(
                appContainer = appContainer,
                onGameClick = { gameId ->
                    rootNavController.navigate(NavRoutes.gameDetail(gameId))
                },
                onMoreItemClick = { entry ->
                    when (entry) {
                        MoreEntry.COLLECTIONS -> rootNavController.navigate(NavRoutes.COLLECTIONS)
                        MoreEntry.APPEARANCE -> rootNavController.navigate(NavRoutes.SETTINGS_APPEARANCE)
                        MoreEntry.STATISTICS -> rootNavController.navigate(NavRoutes.STATISTICS)
                        MoreEntry.SECURITY -> rootNavController.navigate(NavRoutes.SECURITY_INFO)
                        MoreEntry.ADVANCED -> rootNavController.navigate(NavRoutes.ADVANCED)
                        MoreEntry.DATA_STORAGE -> rootNavController.navigate(NavRoutes.DATA_STORAGE)
                        MoreEntry.SETTINGS -> rootNavController.navigate(NavRoutes.SETTINGS)
                        MoreEntry.ABOUT -> rootNavController.navigate(NavRoutes.ABOUT)
                    }
                },
                onSourceClick = { sourceId ->
                    rootNavController.navigate(NavRoutes.sourceBrowse(sourceId))
                },
            )
        }

        composable(
            NavRoutes.SOURCE_BROWSE,
            arguments = listOf(navArgument("sourceId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sourceId = backStackEntry.arguments?.getString("sourceId") ?: return@composable
            SourceBrowseScreen(
                sourceId = sourceId,
                sourceManager = appContainer.sourceManager,
                gameRepository = appContainer.gameRepository,
                appSettings = appContainer.appSettings,
                onNavigateBack = { rootNavController.popBackStack() },
                onAddGame = {
                    rootNavController.navigate(NavRoutes.ADD_GAME)
                },
                onNavigateToDetail = { gameId ->
                    rootNavController.navigate(NavRoutes.gameDetail(gameId))
                },
            )
        }

        composable(NavRoutes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                key = "shared_settings_vm",
                factory = SettingsViewModel.Factory(
                    appSettings = appContainer.appSettings,
                    repository = appContainer.gameRepository,
                )
            )
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { rootNavController.popBackStack() },
            )
        }

        composable(NavRoutes.COLLECTIONS) {
            val viewModel: CollectionsViewModel = viewModel(
                factory = CollectionsViewModel.Factory(appContainer.gameRepository),
            )
            CollectionsScreen(
                viewModel = viewModel,
                onNavigateBack = { rootNavController.popBackStack() },
            )
        }

        composable(NavRoutes.DATA_STORAGE) {
            val context = LocalContext.current.applicationContext
            val viewModel: DataStorageViewModel = viewModel(
                factory = DataStorageViewModel.Factory(
                    appContainer.gameVaultBackup,
                    appContainer.appSettings,
                    context,
                ),
            )
            DataStorageScreen(
                viewModel = viewModel,
                onNavigateBack = { rootNavController.popBackStack() },
            )
        }

        composable(NavRoutes.SETTINGS_APPEARANCE) {
            val viewModel: SettingsViewModel = viewModel(
                key = "shared_settings_vm",
                factory = SettingsViewModel.Factory(
                    appSettings = appContainer.appSettings,
                    repository = appContainer.gameRepository,
                )
            )
            AppearanceScreen(
                viewModel = viewModel,
                onNavigateBack = { rootNavController.popBackStack() },
            )
        }

        composable(NavRoutes.STATISTICS) {
            val viewModel: StatisticsViewModel = viewModel(
                factory = StatisticsViewModel.Factory(appContainer.gameRepository),
            )
            StatisticsScreen(
                viewModel = viewModel,
                onNavigateBack = { rootNavController.popBackStack() },
            )
        }

        composable(NavRoutes.SECURITY_INFO) {
            val viewModel: SecurityViewModel = viewModel(
                factory = SecurityViewModel.Factory(appContainer.appSettings),
            )
            SecurityScreen(
                viewModel = viewModel,
                onNavigateBack = { rootNavController.popBackStack() },
            )
        }

        composable(NavRoutes.ADVANCED) {
            val viewModel: AdvancedViewModel = viewModel(
                factory = AdvancedViewModel.Factory(appContainer.appSettings),
            )
            AdvancedScreen(
                viewModel = viewModel,
                onNavigateBack = { rootNavController.popBackStack() },
            )
        }

        composable(NavRoutes.ABOUT) {
            AboutScreen(
                onNavigateBack = { rootNavController.popBackStack() },
            )
        }

        composable(NavRoutes.ADD_GAME) {
            val viewModel: AddGameViewModel = viewModel(
                factory = AddGameViewModel.Factory(appContainer.gameRepository, appContainer.f95ZoneScraper, appContainer.appSettings),
            )
            AddGameScreen(
                viewModel = viewModel,
                onNavigateBack = { rootNavController.popBackStack() },
            )
        }

        composable(
            route = NavRoutes.GAME_DETAIL,
            arguments = listOf(
                navArgument("gameId") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getLong("gameId") ?: return@composable
            val viewModel: GameDetailViewModel = viewModel(
                key = "game_$gameId",
                factory = GameDetailViewModel.Factory(
                    gameId,
                    appContainer.gameRepository,
                    appContainer.appSettings,
                ),
            )
            GameDetailScreen(
                viewModel = viewModel,
                onBack = { rootNavController.popBackStack() },
            )
        }

        composable(
            route = NavRoutes.COLLECTION_GAMES,
            arguments = listOf(
                navArgument("collectionId") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val collectionId = backStackEntry.arguments?.getLong("collectionId") ?: return@composable
            val viewModel: LibraryViewModel = viewModel(
                key = "collection_$collectionId",
                factory = LibraryViewModel.Factory(appContainer.gameRepository, appContainer.appSettings),
            )
            LaunchedEffect(collectionId) {
                viewModel.onCollectionFilterChanged(collectionId)
            }
            LibraryScreen(
                viewModel = viewModel,
                onGameClick = { gameId ->
                    rootNavController.navigate(NavRoutes.gameDetail(gameId))
                },
            )
        }
    }
}

@Composable
private fun MainTabsScreen(
    appContainer: AppContainer,
    onGameClick: (Long) -> Unit,
    onMoreItemClick: (MoreEntry) -> Unit,
    onSourceClick: (String) -> Unit,
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    val historyViewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.Factory(appContainer.gameRepository),
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTabIndex) {
                0 -> {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "library_tab",
                    ) {
                        composable("library_tab") {
                            val viewModel: LibraryViewModel = viewModel(
                                factory = LibraryViewModel.Factory(appContainer.gameRepository, appContainer.appSettings),
                            )
                            LibraryScreen(
                                viewModel = viewModel,
                                onGameClick = onGameClick,
                            )
                        }
                    }
                }

                1 -> {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "history_tab",
                    ) {
                        composable("history_tab") {
                            HistoryScreen(
                                viewModel = historyViewModel,
                                onGameClick = onGameClick,
                            )
                        }
                    }
                }

                2 -> {
                    BrowserScreen(
                        sourceManager = appContainer.sourceManager,
                        appSettings = appContainer.appSettings,
                        onSourceClick = onSourceClick,
                    )
                }

                3 -> {
                    MoreScreen(
                        onItemClick = onMoreItemClick,
                    )
                }
            }
        }
    }
}
