package com.gamevault.app.domain.model

/**
 * A user-defined collection/category (e.g. "RPGs", "Favorites", "2025 Completed").
 * Maps to Mihon's categories.
 */
data class Collection(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val color: Long? = null,       // ARGB color
    val order: Int = 0,
)
