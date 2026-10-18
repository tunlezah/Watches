package io.github.tunlezah.watches.ui.platform

import androidx.compose.runtime.Composable

/** Keeps the screen awake while this is in the composition, for long backups and restores. */
@Composable
expect fun KeepScreenOn()
