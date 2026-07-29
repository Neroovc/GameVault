package com.gamevault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gamevault.app.ui.detail.GameDetailScreen
import com.gamevault.app.ui.detail.GameDetailViewModel
import com.gamevault.app.ui.library.LibraryScreen
import com.gamevault.app.ui.library.LibraryViewModel
import com.gamevault.app.ui.navigation.NavRoutes
import com.gamevault.app.ui.theme.GameVaultTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as GameVaultApp).appContainer

        setContent {
            GameVaultTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GameVaultNavHost(
                        gameRepository = appContainer.gameRepository,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameVaultNavHost(
    gameRepository: com.gamevault.app.domain.repository.GameRepository,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.LIBRARY,
    ) {
        // Library screen
        composable(NavRoutes.LIBRARY) {
            val viewModel: LibraryViewModel = viewModel(
                factory = LibraryViewModel.Factory(gameRepository),
            )
            LibraryScreen(
                viewModel = viewModel,
                onGameClick = { gameId ->
                    navController.navigate(NavRoutes.gameDetail(gameId))
                },
                onAddGame = {
                    // TODO: Navigate to add game screen
                },
            )
        }

        // Game detail screen
        composable(
            route = NavRoutes.GAME_DETAIL,
            arguments = listOf(
                navArgument("gameId") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getLong("gameId") ?: return@composable
            val viewModel: GameDetailViewModel = viewModel(
                key = "game_$gameId",
                factory = GameDetailViewModel.Factory(gameId, gameRepository),
            )
            GameDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
