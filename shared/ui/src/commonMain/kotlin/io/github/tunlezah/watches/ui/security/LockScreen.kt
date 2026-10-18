package io.github.tunlezah.watches.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.IconBadge
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme

/** Covers the app while it's locked. Asks for the unlock straight away, and again on a tap. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    LaunchedEffect(Unit) { onUnlock() }
    Box(
        Modifier.fillMaxSize().background(c.bg)
            // Swallows touches so nothing underneath can be used.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 360.dp).padding(32.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(WatchIcons.Lock, size = 72)
            Spacer(Modifier.height(4.dp))
            Text(t.title("Watches is locked"), style = t.type.groupTitle, color = c.fg, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text("Unlock with your fingerprint, face or phone PIN.", style = t.type.bodySmall, color = c.fg2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            WatchButton("Unlock", onUnlock, Modifier.fillMaxWidth(), kind = ButtonKind.Primary, icon = WatchIcons.Lock)
        }
    }
}
