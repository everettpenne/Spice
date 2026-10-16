package com.everettpenne.spice.ui.screens

import com.everettpenne.updater.collectStatus
import com.everettpenne.glass.GlassAlert
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import com.everettpenne.spice.BuildConfig
import com.everettpenne.spice.graph
import com.everettpenne.spice.ui.components.bottomContentPadding
import com.everettpenne.spice.ui.components.topContentPadding
import com.everettpenne.glass.*
import com.everettpenne.spice.ui.theme.Spice
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val scope = rememberCoroutineScope()
    val session by graph.sessions.session.collectAsState()
    val direct by graph.settings.directPlay.collectAsState()
    val force by graph.settings.forceTranscode.collectAsState()
    val autoNext by graph.settings.autoplayNext.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableStateOf(SingletonImageLoader.get(context).diskCache?.size ?: 0L) }

    GlassScreen(bars = { GlassCenteredTopBar("Settings", onBack, Modifier.align(Alignment.TopCenter)) }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = topContentPadding(), bottom = bottomContentPadding(), start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Group("Server") {
                Row(session?.let { "${it.serverName.ifEmpty { "Jellyfin" }}" } ?: "Not signed in", Icons.Rounded.Dns, session?.serverUrl)
                Row(session?.userName ?: "-", Icons.Rounded.Person, "Signed in on this device")
                NavRow(Icons.Rounded.Logout, "Sign Out", null) { confirmSignOut = true }
            }
            Group("Playback", footer = "Direct play sends the original file, with no quality loss and no work for the server. If the phone cannot play it, Spice asks the server to transcode instead.") {
                SwitchRow(Icons.Rounded.PlayArrow, "Try Direct Play First", direct && !force) { graph.settings.setDirectPlay(it); if (it) graph.settings.setForceTranscode(false) }
                SwitchRow(Icons.Rounded.Speed, "Always Transcode", force) { graph.settings.setForceTranscode(it); if (it) graph.settings.setDirectPlay(false) }
                SwitchRow(Icons.Rounded.SkipNext, "Play Next Episode Automatically", autoNext, graph.settings::setAutoplayNext)
            }
            Group("Storage") {
                NavRow(Icons.Rounded.DeleteSweep, "Clear Image Cache", "%.1f MB".format(cacheBytes / 1_048_576.0)) {
                    val loader = SingletonImageLoader.get(context)
                    loader.memoryCache?.clear(); loader.diskCache?.clear(); cacheBytes = loader.diskCache?.size ?: 0L
                }
            }
            Group("About") {
                com.everettpenne.updater.LocalUpdater.current?.let { u -> NavRow(androidx.compose.material.icons.Icons.Rounded.SystemUpdate, "Check for Updates", u.collectStatus()) { u.onSettingsRowClick() } }
                Row("Spice ${BuildConfig.VERSION_NAME}", Icons.Rounded.Info, "A Jellyfin client with iOS-style liquid glass")
                NavRow(Icons.Rounded.Code, "Jellyfin", "Spice talks to your own Jellyfin server; nothing goes anywhere else") {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/jellyfin")))
                }
            }
            Text("Spice is an independent app and is not affiliated with the Jellyfin project.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        }
        if (confirmSignOut) GlassAlert("Sign out of ${session?.serverName?.ifEmpty { "this server" } ?: "this server"}?", "This ends the session on the server and removes it from this phone.", "Sign Out",
            { confirmSignOut = false; scope.launch { graph.api.logout(); graph.sessions.signOut(); onSignedOut() } }, { confirmSignOut = false }, destructive = true)
    }
}

@Composable
private fun Group(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(26.dp)), content = content)
        if (footer != null) Text(footer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp))
    }
}

@Composable
private fun RowShell(icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)?, trailing: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = Spice.accent, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable private fun Row(title: String, icon: ImageVector, subtitle: String?) = RowShell(icon, title, subtitle, null) {}
@Composable private fun NavRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) =
    RowShell(icon, title, subtitle, onClick) { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    RowShell(icon, title, null, { onChange(!checked) }) { LiquidSwitch(checked, onChange) }
