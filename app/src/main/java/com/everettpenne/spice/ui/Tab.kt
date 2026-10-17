package com.everettpenne.spice.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.everettpenne.glass.GlassTabSpec

/** Spice's top-level destinations. Home, Library and Music sit in the glass tab capsule; Search is the round button beside it. */
enum class Tab(override val route: String, override val label: String, override val icon: ImageVector) : GlassTabSpec {
    Home("home", "Home", Icons.Rounded.Home),
    Library("library", "Library", Icons.Rounded.VideoLibrary),
    Music("music", "Music", Icons.Rounded.MusicNote),
    Search("search", "Search", Icons.Rounded.Search);

    companion object { val bar = listOf(Home, Library, Music) }
}
