package com.everettpenne.spice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.everettpenne.spice.data.api.ApiResult
import com.everettpenne.spice.data.model.PublicInfo
import com.everettpenne.spice.graph
import com.everettpenne.spice.ui.components.GlassTextField
import com.everettpenne.glass.GlassButton
import com.everettpenne.glass.GlassScreen
import com.everettpenne.glass.GlassTopBar
import com.everettpenne.spice.ui.theme.Spice
import kotlinx.coroutines.launch

/** Two steps: find the server, then sign in to it. Nothing but the server address, the username and the password is ever typed or sent. */
@Composable
fun LoginScreen(onDone: () -> Unit, onCancel: (() -> Unit)? = null) {
    val graph = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<Pair<String, PublicInfo>?>(null) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun connect() {
        if (busy || address.isBlank()) return
        busy = true; error = null
        scope.launch {
            when (val r = graph.api.connect(address)) { is ApiResult.Ok -> found = r.value; is ApiResult.Failed -> error = r.reason.message }
            busy = false
        }
    }
    fun signIn() {
        val (url, info) = found ?: return
        if (busy || user.isBlank()) return
        busy = true; error = null
        scope.launch {
            val result = graph.api.login(url, info.serverName, user.trim(), pass)
            // Navigation must run on the main thread whichever thread the network answered on.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                when (result) {
                    is ApiResult.Ok -> { graph.sessions.signIn(result.value); onDone() }
                    is ApiResult.Failed -> error = result.reason.message
                }
                busy = false
            }
        }
    }

    GlassScreen(bars = { GlassTopBar(if (found == null) "Connect" else "Sign in", Modifier.align(Alignment.TopCenter), onBack = if (found != null) ({ found = null; error = null }) else onCancel) }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(top = 96.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Dns, null, tint = Spice.accent, modifier = Modifier.size(56.dp))
            val info = found?.second
            if (info == null) {
                Text("Your Jellyfin server", style = MaterialTheme.typography.titleLarge)
                Text("Enter its address, such as jellyfin.example.com or 192.168.1.20:8096. Spice tries https first, then plain http for a server at home.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                GlassTextField(address, { address = it }, "Server address", leading = Icons.Rounded.Link, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, onSubmit = ::connect)
                GlassButton(if (busy) "Connecting…" else "Connect", ::connect, Modifier.fillMaxWidth(), enabled = !busy && address.isNotBlank(), prominent = true)
            } else {
                Text(info.serverName, style = MaterialTheme.typography.titleLarge)
                Text(found!!.first + (if (info.version.isNotEmpty()) "  ·  Jellyfin ${info.version}" else ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (found!!.first.startsWith("http://")) Text("This address is not encrypted. Your password and what you watch can be seen by anyone on the network between here and the server.",
                    style = MaterialTheme.typography.bodySmall, color = Spice.orange)
                GlassTextField(user, { user = it }, "Username", leading = Icons.Rounded.Person, imeAction = ImeAction.Next)
                GlassTextField(pass, { pass = it }, "Password", leading = Icons.Rounded.Lock, password = true, imeAction = ImeAction.Go, onSubmit = ::signIn)
                GlassButton(if (busy) "Signing in…" else "Sign in", ::signIn, Modifier.fillMaxWidth(), enabled = !busy && user.isNotBlank(), prominent = true)
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Spice.red) }
        }
    }
}
