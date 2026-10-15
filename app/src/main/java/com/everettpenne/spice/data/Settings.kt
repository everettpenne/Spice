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

    /** Try the original file first; fall back to a transcode only when it will not play. */
    val directPlay: StateFlow<Boolean> = _directPlay.asStateFlow()
    /** Start the next episode when one ends. */
    val autoplayNext: StateFlow<Boolean> = _autoplayNext.asStateFlow()
    /** Always ask the server to transcode (for a slow or metered connection). */
    val forceTranscode: StateFlow<Boolean> = _forceTranscode.asStateFlow()

    fun setDirectPlay(v: Boolean) = set("directPlay", _directPlay, v)
    fun setAutoplayNext(v: Boolean) = set("autoplayNext", _autoplayNext, v)
    fun setForceTranscode(v: Boolean) = set("forceTranscode", _forceTranscode, v)
}
