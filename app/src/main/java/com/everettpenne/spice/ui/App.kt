package com.everettpenne.spice.ui

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.everettpenne.spice.graph
import com.everettpenne.spice.ui.components.Navigator
import com.everettpenne.glass.*
import com.everettpenne.spice.ui.screens.*
import com.everettpenne.spice.ui.theme.SpiceTheme
import com.kyant.backdrop.backdrops.layerBackdrop

object Routes {
    const val HOME = "home"; const val LIBRARY = "library"; const val MUSIC = "music"; const val SEARCH = "search"; const val SETTINGS = "settings"; const val LOGIN = "login"
    const val NOW_PLAYING = "nowplaying"; const val DOWNLOADS = "downloads"
    fun item(id: String) = "item/${Uri.encode(id)}"
    fun play(id: String) = "play/${Uri.encode(id)}"
    fun artist(id: String) = "artist/${Uri.encode(id)}"
    fun album(id: String) = "album/${Uri.encode(id)}"
    fun browse(parent: String?, title: String, types: String?, filter: String?) =
        "browse?parent=${Uri.encode(parent ?: "")}&title=${Uri.encode(title)}&types=${Uri.encode(types ?: "")}&filter=${Uri.encode(filter ?: "")}"
}

@Composable
fun SpiceRoot() {
    SpiceTheme {
        val updater = com.everettpenne.updater.Updater.get(LocalContext.current, com.everettpenne.updater.UpdateConfig("everettpenne/Spice", com.everettpenne.spice.BuildConfig.VERSION_NAME))
        // Debug builds skip the launch check so the emulator tests never meet a dialog; Settings > Check for Updates still works there.
        LaunchedEffect(Unit) { if (!com.everettpenne.spice.BuildConfig.DEBUG) updater.checkOnLaunch() }
        com.everettpenne.updater.UpdateDialog(updater, "Spice")
        val graph = LocalContext.current.graph
        val session by graph.sessions.session.collectAsState()
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val signedIn = session != null

        // Signing out (here or by the server ending the session) always lands on the sign-in screen, and signing in lands on Home.
        LaunchedEffect(signedIn) {
            if (!signedIn) navController.navigate(Routes.LOGIN) { popUpTo(navController.graph.id) { inclusive = true }; launchSingleTop = true }
        }

        val nav = remember(navController) {
            object : Navigator {
                override fun openItem(id: String) = navController.navigate(Routes.item(id))
                override fun openBrowse(parentId: String?, title: String, types: String?, filter: String?) = navController.navigate(Routes.browse(parentId, title, types, filter))
                override fun play(id: String) = navController.navigate(Routes.play(id))
                override fun openArtist(id: String) = navController.navigate(Routes.artist(id))
                override fun openAlbum(id: String) = navController.navigate(Routes.album(id))
            }
        }

        val tabs = Tab.entries.associateBy { it.route }
        var lastTab by remember { mutableStateOf(Tab.Home) }
        LaunchedEffect(route) { tabs[route]?.let { lastTab = it } }
        val hideTabs = route == Routes.LOGIN || route?.startsWith("play/") == true || route == Routes.NOW_PLAYING
        val tabBar: (@Composable () -> Unit)? = if (!hideTabs && signedIn) ({
            Column(Modifier.fillMaxWidth()) {
                MiniPlayer(onOpen = { navController.navigate(Routes.NOW_PLAYING) { launchSingleTop = true } })
                GlassTabBar(Tab.bar, selected = lastTab, search = Tab.Search, onSelect = { tab -> navController.navigate(tab.route) { popUpTo(Routes.HOME) { inclusive = false }; launchSingleTop = true } })
            }
        }) else null

        CompositionLocalProvider(com.everettpenne.updater.LocalUpdater provides updater, LocalTabBar provides tabBar) {
            // The plain page colour is also a glass backdrop, recorded in a sibling of the screens: recording the screens themselves would make
            // any glass control inside them sample a layer that contains it, which sends the renderer into endless recursion.
            val pageBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop).background(MaterialTheme.colorScheme.background))
                CompositionLocalProvider(LocalGlassBackdrop provides pageBackdrop) {
                    NavHost(navController, startDestination = if (signedIn) Routes.HOME else Routes.LOGIN, modifier = Modifier.fillMaxSize(),
                        enterTransition = { fadeIn() }, exitTransition = { fadeOut() }, popEnterTransition = { fadeIn() }, popExitTransition = { fadeOut() }) {
                        composable(Routes.LOGIN) {
                            LoginScreen(onDone = { navController.navigate(Routes.HOME) { popUpTo(navController.graph.id) { inclusive = true } } })
                        }
                        composable(Routes.HOME) { HomeScreen(nav, onSettings = { navController.navigate(Routes.SETTINGS) }, onDownloads = { navController.navigate(Routes.DOWNLOADS) }) }
                        composable(Routes.LIBRARY) { LibraryScreen(nav) }
                        composable(Routes.MUSIC) { MusicScreen(nav) }
                        composable(Routes.SEARCH) { SearchScreen(nav) }
                        composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }, onSignedOut = {}, onDownloads = { navController.navigate(Routes.DOWNLOADS) }) }
                        composable(Routes.DOWNLOADS) { DownloadsScreen(nav, onBack = { navController.popBackStack() }) }
                        composable(Routes.NOW_PLAYING) { NowPlayingScreen(onBack = { navController.popBackStack() }) }
                        composable("artist/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
                            MusicArtistScreen(e.arguments?.getString("id").orEmpty(), nav, onBack = { navController.popBackStack() })
                        }
                        composable("album/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
                            MusicAlbumScreen(e.arguments?.getString("id").orEmpty(), nav, onBack = { navController.popBackStack() })
                        }
                        composable("browse?parent={parent}&title={title}&types={types}&filter={filter}", listOf(
                            navArgument("parent") { type = NavType.StringType; defaultValue = "" }, navArgument("title") { type = NavType.StringType; defaultValue = "" },
                            navArgument("types") { type = NavType.StringType; defaultValue = "" }, navArgument("filter") { type = NavType.StringType; defaultValue = "" })) { e ->
                            val a = e.arguments
                            BrowseScreen(a?.getString("parent")?.takeIf { it.isNotEmpty() }, a?.getString("title").orEmpty(), a?.getString("types")?.takeIf { it.isNotEmpty() },
                                a?.getString("filter")?.takeIf { it.isNotEmpty() }, nav, onBack = { navController.popBackStack() })
                        }
                        composable("item/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
                            DetailScreen(e.arguments?.getString("id").orEmpty(), nav, onBack = { navController.popBackStack() })
                        }
                        composable("play/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
                            PlayerScreen(e.arguments?.getString("id").orEmpty(), nav, onBack = { navController.popBackStack() },
                                onSwitch = { next -> navController.navigate(Routes.play(next)) { popUpTo(route ?: Routes.HOME) { inclusive = true } } })
                        }
                    }
                }
            }
        }
    }
}
