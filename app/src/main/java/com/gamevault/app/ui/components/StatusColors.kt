package com.gamevault.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.gamevault.app.domain.model.GameStatus

/**
 * Single source of truth for the accent color of a [GameStatus]. Shared between
 * [GameCard] status styling and the library group headers so both stay in sync.
 */
@Composable
internal fun statusColor(status: GameStatus): Color = when (status) {
    GameStatus.NOT_STARTED -> MaterialTheme.colorScheme.surfaceContainerHighest
    GameStatus.PLAYING -> MaterialTheme.colorScheme.primary
    GameStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
    GameStatus.REPLAYING -> MaterialTheme.colorScheme.secondary
    GameStatus.PAUSED -> MaterialTheme.colorScheme.outline
    GameStatus.ABANDONED -> MaterialTheme.colorScheme.error
}