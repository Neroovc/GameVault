package com.gamevault.app.data.local

/**
 * Options controlling which data groups are included in a backup,
 * modeled after Mihon's backup creation options.
 *
 * Property names are the source of truth for the group strings written to
 * `BackupData.includedGroups` (see the GROUP_* constants in [GameVaultBackup]);
 * keep the two in sync when adding or renaming a group.
 */
data class BackupOptions(
    /** Games plus their changelog and dev/download links. */
    val libraryEntries: Boolean = true,
    /** Collections and game-collection memberships. */
    val collections: Boolean = true,
    /** Play sessions and routes. */
    val history: Boolean = true,
    /** Tags and game-tag assignments. */
    val tags: Boolean = true,
    /** App settings (theme, palette, sources, etc.). */
    val appSettings: Boolean = true,
    /**
     * Modifier for [appSettings], NOT a standalone group: ignored by [canCreate].
     * When true, the settings snapshot includes the saved cookies; when false
     * (default) the cookie fields stay absent from the backup so a restore
     * never touches local cookies.
     */
    val privateSettings: Boolean = false,
) {
    fun canCreate(): Boolean = libraryEntries || collections || history || tags || appSettings

    companion object {
        val ALL = BackupOptions()
    }
}

/**
 * Options controlling which data groups a restore applies to the device.
 * Each flag intersects with the groups actually present in the backup file,
 * so unchecking a group skips it even when the file contains its data.
 */
data class RestoreOptions(
    val libraryEntries: Boolean = true,
    val collections: Boolean = true,
    val history: Boolean = true,
    val tags: Boolean = true,
    val appSettings: Boolean = true,
) {
    fun canRestore(): Boolean = libraryEntries || collections || history || tags || appSettings

    companion object {
        val ALL = RestoreOptions()
    }
}
