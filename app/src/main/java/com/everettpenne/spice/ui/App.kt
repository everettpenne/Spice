package com.everettpenne.spice.ui

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.everettpenne.spice.ui.glass.*
import com.everettpenne.spice.ui.screens.*
import com.everettpenne.spice.ui.theme.SpiceTheme
import com.kyant.backdrop.backdrops.layerBackdrop

object Routes {
    const val HOME = "home"; const val LIBRARY = "library"; const val SEARCH = "search"; const val SETTINGS = "settings"; const val LOGIN = "login"
    fun item(id: String) = "item/${Uri.encode(id)}"
    fun play(id: String) = "play/${Uri.encode(id)}"
    fun browse(parent: String?, title: String, types: String?, filter: String?) =
        "browse?parent=${Uri.encode(parent ?: "")}&title=${Uri.encode(title)}&types=${Uri.encode(types ?: "")}&filter=${Uri.encode(filter ?: "")}"
}

@Composable
fun SpiceRoot() {
    SpiceTheme {
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
            }
        }

        val tabs = Tab.entries.associateBy { it.route }
        var lastTab by remember { mutableStateOf(Tab.Home) }
        LaunchedEffect(route) { tabs[route]?.let { lastTab = it } }
        val hideTabs = route == Routes.LOGIN || route?.startsWith("play/") == true
        val tabBar: (@Composable () -> Unit)? = if (!hideTabs && signedIn) ({
            GlassTabBar(selected = lastTab, onSelect = { tab -> navController.navigate(tab.route) { popUpTo(Routes.HOME) { inclusive = false }; launchSingleTop = true } })
        }) else null

        CompositionLocalProvider(LocalTabBar provides tabBar) {
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
                        composable(Routes.HOME) { HomeScreen(nav, onSettings = { navController.navigate(Routes.SETTINGS) }) }
                        composable(Routes.LIBRARY) { LibraryScreen(nav) }
                        composable(Routes.SEARCH) { SearchScreen(nav) }
                        composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }, onSignedOut = {}) }
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
                            PlayerScreen(e.arguments?.getString("id").orEmpty(), onBack = { navController.popBackStack() },
                                onPlayNext = { next -> navController.navigate(Routes.play(next)) { popUpTo(route ?: Routes.HOME) { inclusive = true } } })
                        }
                    }
                }
            }
        }
    }
}
