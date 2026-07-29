package com.gamevault.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gamevault.app.domain.model.GameRoute
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.domain.model.RouteStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    viewModel: GameDetailViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.game?.title ?: "Game") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading || uiState.game == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (uiState.isLoading) "Loading..." else "Game not found")
            }
        } else {
            val game = uiState.game!!

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Cover + basic info
                item {
                    GameHeader(
                        game = game,
                        onStatusChange = viewModel::updateGameStatus,
                        onRatingChange = viewModel::updatePersonalRating,
                    )
                }

                // Status selector
                item {
                    StatusSelector(
                        current = game.status,
                        onSelected = viewModel::updateGameStatus,
                    )
                }

                // Play session controls
                item {
                    PlaySessionControls(
                        isPlaying = uiState.sessions.any { it.endTime == null },
                        onStart = viewModel::startPlaySession,
                        onStop = {
                            val activeSession = uiState.sessions.find { it.endTime == null }
                            activeSession?.let { viewModel.endPlaySession(it.id) }
                        },
                        totalPlayTime = uiState.totalPlayTime,
                    )
                }

                // Routes section
                item {
                    RoutesSectionHeader(onAddRoute = { name -> viewModel.addRoute(name) })
                }

                items(uiState.routes, key = { it.id }) { route ->
                    RouteItem(
                        route = route,
                        onProgressChange = { viewModel.updateRouteProgress(route.id, it) },
                        onDelete = { viewModel.deleteRoute(route) },
                    )
                }

                // Notes
                if (game.notes != null) {
                    item {
                        NotesSection(notes = game.notes)
                    }
                }

                // Source info
                item {
                    SourceInfo(game = game)
                }

                // Bottom spacer
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun GameHeader(
    game: com.gamevault.app.domain.model.Game,
    onStatusChange: (GameStatus) -> Unit,
    onRatingChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Cover
        Card(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(3f / 4f),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (game.coverUrl != null) {
                AsyncImage(
                    model = game.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleLarge,
            )

            if (game.developer != null) {
                Text(
                    text = game.developer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (game.engine != null) {
                Text(
                    text = game.engine.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Rating
            RatingBar(
                rating = game.personalRating ?: 0f,
                onRatingChange = onRatingChange,
            )
        }
    }
}

@Composable
private fun RatingBar(
    rating: Float,
    onRatingChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { star ->
            val filled = rating >= star
            IconButton(onClick = {
                val newRating = if (rating == star.toFloat()) star - 0.5f else star.toFloat()
                onRatingChange(newRating.coerceIn(0.5f, 5.0f))
            }) {
                Icon(
                    imageVector = if (filled) Icons.Filled.Star
                    else if (rating >= star - 0.5f) Icons.Filled.Star // half visual
                    else Icons.Outlined.StarBorder,
                    contentDescription = "Star $star",
                    tint = if (filled || rating >= star - 0.5f)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text = if (rating > 0) String.format("%.1f", rating) else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusSelector(
    current: GameStatus,
    onSelected: (GameStatus) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Status", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                GameStatus.entries.forEach { status ->
                    FilledTonalButton(
                        onClick = { onSelected(status) },
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(
                            status.displayName,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaySessionControls(
    isPlaying: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    totalPlayTime: Long,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Play Time", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = formatPlayTime(totalPlayTime),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Button(
                onClick = if (isPlaying) onStop else onStart,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isPlaying) "Stop" else "Start Playing")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutesSectionHeader(
    onAddRoute: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Routes / Endings", style = MaterialTheme.typography.titleSmall)
        FilledTonalButton(onClick = { onAddRoute("New Route") }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add Route", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RouteItem(
    route: GameRoute,
    onProgressChange: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (route.status) {
                RouteStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = route.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "${route.progress}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { route.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = route.status.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotesSection(notes: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Notes", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SourceInfo(game: com.gamevault.app.domain.model.Game) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Source", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${game.sourceType.displayName} · ${game.version ?: "N/A"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (game.f95Url != null) {
                Text(
                    text = game.f95Url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatPlayTime(minutes: Long): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}
