package com.gamevault.app.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gamevault.app.data.settings.AppSettings
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.repository.GameRepository
import com.gamevault.app.domain.source.GameSource
import com.gamevault.app.domain.source.SearchResult
import com.gamevault.app.domain.source.SourceManager
import com.gamevault.app.domain.source.SourceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Browse a single game source: debounced search, result grid, detail fetch
 * and confirm dialog to add a game to the library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceBrowseScreen(
    sourceId: String,
    sourceManager: SourceManager,
    gameRepository: GameRepository,
    appSettings: AppSettings,
    onNavigateBack: () -> Unit,
) {
    val source = remember(sourceId) { sourceManager.getById(sourceId) }
    if (source == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Source not found", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onNavigateBack) { Text("Go back") }
            }
        }
        return
    }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingGame by remember { mutableStateOf<Game?>(null) }
    var adding by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Screen-level cache of lazily fetched covers: url -> cover (null = fetched
    // and failed, so we never retry within this screen session).
    val coverCache = remember { mutableStateMapOf<String, String?>() }

    // Debounced search. Cancelled automatically when [query] changes.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            error = null
            loading = false
            return@LaunchedEffect
        }
        delay(500)
        loading = true
        error = null
        // Scrapers block on Jsoup network I/O — run off the main thread,
        // mirroring AddGameViewModel's Dispatchers.IO pattern.
        val res = withContext(Dispatchers.IO) { source.search(query.trim()) }
        when (res) {
            is SourceResult.Success -> results = res.data
            is SourceResult.Error -> error = res.message
        }
        loading = false
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(source.name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search ${source.name}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                query.isBlank() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Search ${source.name} to find games",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                results.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No results",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    // 2-column grid mirroring the library's card grid:
                    // same cover ratio, corner radius, title placement and
                    // spacing as GameCard (LibraryScreen config: 12.dp padding,
                    // 12.dp item spacing).
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(results, key = { it.url }) { result ->
                            SearchResultCard(
                                result = result,
                                source = source,
                                coverCache = coverCache,
                                onClick = {
                                    scope.launch {
                                        val detail = withContext(Dispatchers.IO) {
                                            source.fetchDetail(result.url)
                                        }
                                        when (detail) {
                                            is SourceResult.Success -> pendingGame = detail.data
                                            is SourceResult.Error ->
                                                snackbarMessage = "Failed to load: ${detail.message}"
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingGame?.let { game ->
        AlertDialog(
            onDismissRequest = { if (!adding) pendingGame = null },
            title = { Text(game.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (game.coverUrl != null) {
                        AsyncImage(
                            model = game.coverUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Text(
                        text = listOfNotNull(game.engine?.displayName, source.name)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            adding = true
                            // Mirror AddGameViewModel.saveGame() exactly:
                            // saveGame returns the new row id, then the game
                            // lands in the default collection if one is set.
                            val savedId = gameRepository.saveGame(game)
                            val defaultCollectionId = appSettings.defaultCollectionId.first()
                            if (defaultCollectionId != null) {
                                gameRepository.addGameToCollection(savedId, defaultCollectionId)
                            }
                            pendingGame = null
                            adding = false
                            snackbarMessage = "Added to library"
                        }
                    },
                    enabled = !adding,
                ) { Text(if (adding) "Adding..." else "Add to library") }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingGame = null },
                    enabled = !adding,
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    source: GameSource,
    coverCache: MutableMap<String, String?>,
    onClick: () -> Unit,
) {
    // Start from the search thumbnail; lazily enrich from the thread page when
    // missing (F95Zone results carry no thumbnail from Brave). The cache is
    // screen-level, so scrolling away and back does not refetch.
    var cover by remember(result.url) { mutableStateOf(result.thumbnailUrl) }
    LaunchedEffect(result.url, cover) {
        if (cover == null && !coverCache.containsKey(result.url)) {
            val fetched = withContext(Dispatchers.IO) { source.fetchCover(result.url) }
            coverCache[result.url] = fetched
            cover = fetched
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            // Transparent card — the image is the card (same as GameCard).
            containerColor = Color.Transparent,
        ),
    ) {
        Column {
            // ── Cover (same ratio/corners as GameCard) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f),
            ) {
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = result.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Gamepad,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        )
                    }
                }
            }

            // ── Title + meta below the cover (same as GameCard comfortable) ──
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                // "engine · source", or "developer · engine" when the engine is
                // unknown; always falls back to showing at least the source name.
                val meta = when {
                    result.engine != null -> listOf(result.engine, source.name)
                    result.developer != null -> listOf(result.developer, result.engine ?: source.name)
                    else -> listOf(source.name)
                }
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
