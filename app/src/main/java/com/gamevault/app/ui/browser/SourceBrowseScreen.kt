package com.gamevault.app.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.gamevault.app.domain.model.Collection
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.repository.GameRepository
import com.gamevault.app.domain.source.GameSource
import com.gamevault.app.domain.source.SearchResult
import com.gamevault.app.domain.source.SourceManager
import com.gamevault.app.domain.source.SourceResult
import com.gamevault.app.ui.detail.GameDetailScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Semaphore

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
    onAddGame: () -> Unit,
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
    var submittedQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var detailGame by remember { mutableStateOf<Game?>(null) }
    var detailLoadingUrl by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var allCollections by remember { mutableStateOf(emptyList<Collection>()) }
    var defaultCollectionIds by remember { mutableStateOf(emptyList<Long>()) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        allCollections = gameRepository.getAllCollections()
        appSettings.defaultCollectionId.first()?.let { defaultCollectionIds = listOf(it) }
    }

    // Screen-level cache of lazily fetched covers: url -> cover (null = fetched
    // and failed, so we never retry within this screen session).
    val coverCache = remember { mutableStateMapOf<String, String?>() }

    // Cap concurrent cover fetches across the grid: a full grid fires ~20
    // fetchCover calls at once. Scraper-side throttles also apply; this just
    // stops the burst at the screen boundary.
    val coverLimiter = remember { Semaphore(4) }

    // Search runs ONLY when the user presses Enter (IME action). Each search
    // hits external engines and can trigger per-card cover fetches, so typing
    // must stay silent. Cancelled automatically when [submittedQuery] changes.
    LaunchedEffect(submittedQuery) {
        if (submittedQuery.isBlank()) {
            results = emptyList()
            error = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        // Scrapers block on Jsoup network I/O — run off the main thread,
        // mirroring AddGameViewModel's Dispatchers.IO pattern.
        val res = withContext(Dispatchers.IO) { source.search(submittedQuery.trim()) }
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
        floatingActionButton = {
            FloatingActionButton(onClick = onAddGame) {
                Icon(Icons.Default.Add, contentDescription = "Add Game")
            }
        },
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
                keyboardActions = KeyboardActions(onSearch = { submittedQuery = query }),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            submittedQuery = ""
                        }) {
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

                submittedQuery.isBlank() -> {
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
                                coverLimiter = coverLimiter,
                                loading = detailLoadingUrl == result.url,
                                onClick = {
                                    // One detail fetch at a time; give the card
                                    // visible feedback while the thread loads.
                                    if (detailLoadingUrl == null) {
                                        detailLoadingUrl = result.url
                                        scope.launch {
                                            val detail = withContext(Dispatchers.IO) {
                                                source.fetchDetail(result.url)
                                            }
                                            detailLoadingUrl = null
                                            when (detail) {
                                                is SourceResult.Success -> detailGame = detail.data
                                                is SourceResult.Error ->
                                                    snackbarMessage = "Failed to load: ${detail.message}"
                                            }
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

    // System back returns from the detail view to the results.
    BackHandler(enabled = detailGame != null) { detailGame = null }

    val game = detailGame
    if (game != null) {
        // Reuse the library's detail screen in preview mode (same window as a
        // saved game, minus save-only sections) — Mihon-style.
        GameDetailScreen(
            viewModel = null,
            previewGame = game,
            sourceName = source.name,
            addingToLibrary = adding,
            allCollections = allCollections,
            initialCollectionIds = defaultCollectionIds,
            onAddToLibrary = { collectionIds ->
                scope.launch {
                    try {
                        adding = true
                        // Dedup guard: f95_url is UNIQUE and insertGame uses
                        // REPLACE, so re-adding a scraped game would delete the
                        // existing row and cascade its children (routes, play
                        // sessions, cross-refs, notes, status, rating).
                        val existing = game.f95Url?.let { gameRepository.getGameBySourceUrl(it) }
                            ?: game.sourceUrl?.let { gameRepository.getGameBySourceUrl(it) }
                        if (existing != null) {
                            if (existing.inLibrary) {
                                snackbarMessage = "Already in library"
                            } else {
                                // Row survives unmarking, so re-adding must not
                                // re-insert (REPLACE would nuke children). Flip
                                // the flag back and rejoin the chosen collections.
                                gameRepository.setGameInLibrary(existing.id, true)
                                collectionIds.forEach { id ->
                                    gameRepository.addGameToCollection(existing.id, id)
                                }
                                snackbarMessage = "Added to library"
                            }
                            return@launch
                        }
                        // saveGame returns the new row id, then the game lands in
                        // the chosen collections (default collection pre-checked).
                        val savedId = gameRepository.saveGame(game)
                        collectionIds.forEach { id -> gameRepository.addGameToCollection(savedId, id) }
                        snackbarMessage = "Added to library"
                    } finally {
                        adding = false
                        detailGame = null
                    }
                }
            },
            onBack = { if (!adding) detailGame = null },
        )
        return
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    source: GameSource,
    coverCache: MutableMap<String, String?>,
    coverLimiter: Semaphore,
    loading: Boolean,
    onClick: () -> Unit,
) {
    // Start from the search thumbnail; lazily enrich from the thread page when
    // missing (F95Zone results carry no thumbnail from Brave). The cache is
    // screen-level, so scrolling away and back does not refetch.
    var cover by remember(result.url) { mutableStateOf(coverCache[result.url] ?: result.thumbnailUrl) }
    LaunchedEffect(result.url, cover) {
        if (cover == null && !coverCache.containsKey(result.url)) {
            val fetched = withContext(Dispatchers.IO) {
                // try/finally so a cancelled card (scrolled away) never leaks a
                // permit; acquire() itself holds no permit while waiting.
                coverLimiter.acquire()
                try {
                    source.fetchCover(result.url)
                } finally {
                    coverLimiter.release()
                }
            }
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
                // Detail fetch in progress — scrim + spinner on the cover.
                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary,
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
