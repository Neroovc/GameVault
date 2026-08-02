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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.gamevault.app.data.settings.StatusStyle
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameEngine
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.domain.model.SourceType

private val cardShape = RoundedCornerShape(12.dp)
private val statusStripWidth = 4.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCard(
    game: Game,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    gridMode: GridMode = GridMode.COMFORTABLE,
    showEngine: Boolean = true,
    showSource: Boolean = true,
    showStatus: Boolean = true,
    statusStyle: StatusStyle = StatusStyle.TOP_BAR,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            // Transparent card — the image is the card (Mihon style, no gray box
            // behind the title in comfortable/list modes).
            containerColor = Color.Transparent,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (showStatus && statusStyle == StatusStyle.TOP_BAR) {
                        Modifier.statusStrip(statusColor(game.status))
                    } else {
                        Modifier
                    }
                )
        ) {
            if (gridMode == GridMode.LIST) {
                ListContent(
                    game = game,
                    isSelected = isSelected,
                    showEngine = showEngine,
                    showSource = showSource,
                    showStatus = showStatus,
                    statusStyle = statusStyle,
                )
            } else {
                GridContent(
                    game = game,
                    isSelected = isSelected,
                    isCompact = gridMode == GridMode.COMPACT,
                    showEngine = showEngine,
                    showSource = showSource,
                    showStatus = showStatus,
                    statusStyle = statusStyle,
                )
            }
        }
    }
}

@Composable
private fun GridContent(
    game: Game,
    isSelected: Boolean,
    isCompact: Boolean,
    showEngine: Boolean,
    showSource: Boolean,
    showStatus: Boolean,
    statusStyle: StatusStyle,
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

            // Engine + Source badge (top-end) — gated by the overlay settings
            if (showEngine || showSource) {
                EngineSourceBadge(
                    engine = game.engine,
                    sourceType = game.sourceType,
                    showEngine = showEngine,
                    showSource = showSource,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }

            // Status badge (top-start) — gated by the overlay settings and style
            if (showStatus && statusStyle == StatusStyle.BADGE) {
                StatusBadge(
                    status = game.status,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }

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
    showEngine: Boolean,
    showSource: Boolean,
    showStatus: Boolean,
    statusStyle: StatusStyle,
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

            // Status badge (top-start) — gated by the overlay settings and style
            if (showStatus && statusStyle == StatusStyle.BADGE) {
                StatusBadge(
                    status = game.status,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
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

            // Engine + Source as text, gated by the overlay settings
            val meta = buildList {
                if (showEngine) game.engine?.let { add(it.displayName) }
                if (showSource && game.sourceType != SourceType.MANUAL) add(game.sourceType.displayName)
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

/**
 * Brand icon for a known engine (Ren'Py, RPG Maker, Unity, ...).
 * Returns null for engines without a dedicated drawable, so the caller can
 * fall back to a generic Material icon.
 */
@Composable
private fun enginePainter(engine: GameEngine): Painter? = when (engine) {
    GameEngine.RENPY -> painterResource(R.drawable.ic_engine_renpy)
    GameEngine.RPGM -> painterResource(R.drawable.ic_engine_rpgm)
    GameEngine.UNITY -> painterResource(R.drawable.ic_engine_unity)
    GameEngine.UNREAL -> painterResource(R.drawable.ic_engine_unreal)
    GameEngine.FLASH -> painterResource(R.drawable.ic_engine_flash)
    GameEngine.JAVA -> painterResource(R.drawable.ic_engine_java)
    GameEngine.TWINE -> painterResource(R.drawable.ic_engine_twine)
    GameEngine.HTML -> painterResource(R.drawable.ic_engine_html)
    GameEngine.OTHER, GameEngine.UNKNOWN -> null
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
    showEngine: Boolean,
    showSource: Boolean,
    modifier: Modifier = Modifier,
) {
    val showEngineIcon = showEngine && engine != null
    val showSourceIcon = showSource && sourceType != null && sourceType != SourceType.MANUAL
    if (!showEngineIcon && !showSourceIcon) return

    // Komikku-style: engine + source grouped as small icon pills, no text.
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showEngineIcon) {
            BadgeIcon(
                painter = enginePainter(engine),
                contentDescription = engine.displayName,
                fallback = Icons.Filled.QuestionMark,
            )
        }
        if (showSourceIcon) {
            BadgeIcon(
                painter = sourcePainter(sourceType),
                contentDescription = sourceType.displayName,
                fallback = null,
            )
        }
    }
}

@Composable
private fun BadgeIcon(
    painter: Painter?,
    contentDescription: String?,
    fallback: ImageVector?,
) {
    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            // Slight scrim so brand logos float over busy covers, same pill
            // language as RatingPill.
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        ),
    ) {
        if (painter != null) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier
                    .padding(horizontal = 3.dp, vertical = 2.dp)
                    .size(14.dp),
                // Brand logos carry their own colors — no tint.
                tint = Color.Unspecified,
            )
        } else if (fallback != null) {
            Icon(
                imageVector = fallback,
                contentDescription = contentDescription,
                modifier = Modifier
                    .padding(horizontal = 3.dp, vertical = 2.dp)
                    .size(14.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
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

private fun Modifier.statusStrip(color: Color): Modifier = drawWithContent {
    drawContent()
    drawRect(
        color = color,
        topLeft = Offset.Zero,
        size = Size(size.width, statusStripWidth.toPx()),
    )
}

@Composable
private fun statusColor(status: GameStatus): Color = when (status) {
    GameStatus.NOT_STARTED -> MaterialTheme.colorScheme.surfaceContainerHighest
    GameStatus.PLAYING -> MaterialTheme.colorScheme.primary
    GameStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
    GameStatus.REPLAYING -> MaterialTheme.colorScheme.secondary
    GameStatus.PAUSED -> MaterialTheme.colorScheme.outline
    GameStatus.ABANDONED -> MaterialTheme.colorScheme.error
}

/**
 * Colored status label rendered at the cover/thumbnail top-start corner when
 * [StatusStyle.BADGE] is active.
 */
@Composable
private fun StatusBadge(
    status: GameStatus,
    modifier: Modifier = Modifier,
) {
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
        colors = CardDefaults.cardColors(containerColor = statusColor(status)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (status == GameStatus.NOT_STARTED) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.surface
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
