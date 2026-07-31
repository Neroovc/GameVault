package com.gamevault.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gamevault.app.R
import com.gamevault.app.data.settings.GridMode
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameEngine
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.domain.model.SourceType

private val cardShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCard(
    game: Game,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    gridMode: GridMode = GridMode.COMFORTABLE,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        if (gridMode == GridMode.LIST) {
            ListContent(game, isSelected)
        } else {
            GridContent(game, isSelected, isCompact = gridMode == GridMode.COMPACT)
        }
    }
}

@Composable
private fun GridContent(
    game: Game,
    isSelected: Boolean,
    isCompact: Boolean,
) {
    Column {
        // ── Cover ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
        ) {
            if (game.coverUrl != null) {
                AsyncImage(
                    model = game.coverUrl,
                    contentDescription = game.title,
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

            // Selection overlay
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                )
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp),
                )
            }

            // Status badge (top-start)
            StatusBadge(
                status = game.status,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )

            // Engine + Source badge (top-end) — always visible
            EngineSourceBadge(
                engine = game.engine,
                sourceType = game.sourceType,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )

            // Rating pill (bottom-end)
            if (game.personalRating != null) {
                RatingPill(
                    rating = game.personalRating,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }

            // Compact overlay title — NO background box, just text over image
            if (isCompact) {
                Text(
                    text = game.title,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Comfortable: title below the image
        if (!isCompact) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ListContent(
    game: Game,
    isSelected: Boolean,
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        // ── Thumbnail ──
        Box(
            modifier = Modifier
                .width(90.dp)
                .height(IntrinsicSize.Min),
        ) {
            if (game.coverUrl != null) {
                AsyncImage(
                    model = game.coverUrl,
                    contentDescription = game.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Gamepad,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    )
                }
            }

            // Status badge on thumbnail
            StatusBadge(
                status = game.status,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            )

            // Selection overlay
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                )
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp),
                )
            }
        }

        // ── Details ──
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Engine + Source as text
            val meta = buildList {
                game.engine?.let { add(it.displayName) }
                if (game.sourceType != SourceType.MANUAL) add(game.sourceType.displayName)
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Rating row
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (game.personalRating != null) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFFFFB300),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = formatRating(game.personalRating),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                if (game.playTimeMinutes > 0) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = formatPlayTime(game.playTimeMinutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun engineIcon(engine: GameEngine): ImageVector = when (engine) {
    GameEngine.RENPY -> Icons.Filled.AutoStories
    GameEngine.RPGM -> Icons.Filled.Map
    GameEngine.UNITY -> Icons.Filled.ViewInAr
    GameEngine.UNREAL -> Icons.Filled.Flight
    GameEngine.HTML -> Icons.Filled.Code
    GameEngine.FLASH -> Icons.Filled.Bolt
    GameEngine.JAVA -> Icons.Filled.Coffee
    GameEngine.TWINE -> Icons.Filled.Link
    GameEngine.OTHER -> Icons.Filled.QuestionMark
    GameEngine.UNKNOWN -> Icons.Filled.QuestionMark
}

@Composable
private fun sourcePainter(sourceType: SourceType): Painter? = when (sourceType) {
    SourceType.F95ZONE -> painterResource(R.drawable.ic_source_f95zone)
    SourceType.ITCHIO -> painterResource(R.drawable.ic_source_itch)
    else -> null
}

@Composable
private fun EngineSourceBadge(
    engine: GameEngine?,
    sourceType: SourceType?,
    modifier: Modifier = Modifier,
) {
    val showEngine = engine != null
    val showSource = sourceType != null && sourceType != SourceType.MANUAL
    if (!showEngine && !showSource) return

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showEngine) {
                Icon(
                    imageVector = engineIcon(engine),
                    contentDescription = engine.displayName,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = engine.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            if (showSource) {
                val painter = sourcePainter(sourceType)
                if (painter != null) {
                    Icon(
                        painter = painter,
                        contentDescription = sourceType.displayName,
                        modifier = Modifier.size(12.dp),
                    )
                } else {
                    Text(
                        text = sourceType.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun formatPlayTime(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

@Composable
private fun StatusBadge(
    status: GameStatus,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (status) {
        GameStatus.NOT_STARTED -> MaterialTheme.colorScheme.surfaceContainerHighest
        GameStatus.PLAYING -> MaterialTheme.colorScheme.primary
        GameStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
        GameStatus.REPLAYING -> MaterialTheme.colorScheme.secondary
        GameStatus.PAUSED -> MaterialTheme.colorScheme.outline
        GameStatus.ABANDONED -> MaterialTheme.colorScheme.error
    }

    val label = when (status) {
        GameStatus.NOT_STARTED -> "New"
        GameStatus.PLAYING -> "Playing"
        GameStatus.COMPLETED -> "Done"
        GameStatus.REPLAYING -> "Replay"
        GameStatus.PAUSED -> "Paused"
        GameStatus.ABANDONED -> "Dropped"
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = when (status) {
                GameStatus.NOT_STARTED -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.surface
            },
        )
    }
}

@Composable
private fun RatingPill(
    rating: Float,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = Color(0xFFFFB300),
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = formatRating(rating),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun formatRating(rating: Float): String {
    return if (rating == rating.toInt().toFloat()) {
        "${rating.toInt()}.0"
    } else {
        String.format("%.1f", rating)
    }
}
