package com.everettpenne.spice.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app's preferences, as flows the screens watch. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("spice_settings", Context.MODE_PRIVATE)
    private fun flag(key: String, default: Boolean) = MutableStateFlow(prefs.getBoolean(key, default))
    private fun set(key: String, flow: MutableStateFlow<Boolean>, value: Boolean) { flow.value = value; prefs.edit().putBoolean(key, value).apply() }

    private val _directPlay = flag("directPlay", true)
    private val _autoplayNext = flag("autoplayNext", true)
    private val _forceTranscode = flag("forceTranscode", false)
    private val _wifiOnlyDownloads = flag("wifiOnlyDownloads", true)
    private val _linksInApp = flag("linksInApp", true)

    /** Try the original file first; fall back to a transcode only when it will not play. */
    val directPlay: StateFlow<Boolean> = _directPlay.asStateFlow()
    /** Start the next episode when one ends. */
    val autoplayNext: StateFlow<Boolean> = _autoplayNext.asStateFlow()
    /** Always ask the server to transcode (for a slow or metered connection). */
    val forceTranscode: StateFlow<Boolean> = _forceTranscode.asStateFlow()

    /** Downloads wait for Wi-Fi rather than use mobile data (films are large). */
    val wifiOnlyDownloads: StateFlow<Boolean> = _wifiOnlyDownloads.asStateFlow()

    fun setWifiOnlyDownloads(v: Boolean) = set("wifiOnlyDownloads", _wifiOnlyDownloads, v)
    /** Links (an item's IMDb or TheMovieDb page) open in the suite's built-in browser over the app, or in Vanadium or the phone's browser. */
    val linksInApp: StateFlow<Boolean> = _linksInApp.asStateFlow()
    fun setLinksInApp(v: Boolean) = set("linksInApp", _linksInApp, v)
    fun setDirectPlay(v: Boolean) = set("directPlay", _directPlay, v)
    fun setAutoplayNext(v: Boolean) = set("autoplayNext", _autoplayNext, v)
    fun setForceTranscode(v: Boolean) = set("forceTranscode", _forceTranscode, v)
}
