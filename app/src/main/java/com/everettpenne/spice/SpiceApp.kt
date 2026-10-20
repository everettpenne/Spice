package com.everettpenne.spice

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.everettpenne.spice.data.Settings
import com.everettpenne.spice.data.api.JellyfinApi
import com.everettpenne.spice.data.api.MediaUrls
import com.everettpenne.spice.data.auth.SecureStore
import com.everettpenne.spice.data.auth.SessionStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** The app's shared parts, built once: the HTTP client, the encrypted session, the Jellyfin client and the preferences. */
class Graph(app: Context) {
    val sessions = SessionStore(app, SecureStore(app))
    val settings = Settings(app)

    private val serverHost get() = sessions.session.value?.serverUrl?.toHttpUrlOrNull()?.host

    /**
     * One client for the API, the images (Coil) and the video (ExoPlayer). It adds Jellyfin's Authorization header, but only to requests for the
     * signed-in server's own host, so the token cannot leak to anywhere else a link might point.
     */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val session = sessions.session.value
            if (session != null && request.header("Authorization") == null && request.url.host == serverHost) {
                chain.proceed(request.newBuilder().header("Authorization", api.authHeader(session.token)).build())
            } else chain.proceed(request)
        }.build()

    val api: JellyfinApi = JellyfinApi(http, session = { sessions.session.value }, deviceId = { sessions.deviceId })
    val urls = MediaUrls(server = { sessions.session.value?.serverUrl }, deviceId = { sessions.deviceId })

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    val downloads = com.everettpenne.spice.data.Downloads(app, java.io.File(app.getExternalFilesDir(null) ?: app.filesDir, "downloads").apply { mkdirs() }, http,
        auth = { sessions.session.value?.token?.let { api.authHeader(it) } }, posterUrl = { urls.poster(it, 400) }, wifiOnly = { settings.wifiOnlyDownloads.value }, scope = scope)

    /** Saves an item for watching or listening with no connection. False when there is no session to ask the server with. */
    fun download(item: com.everettpenne.spice.data.model.JfItem): Boolean = urls.download(item.id)?.let { downloads.download(item, it) } ?: false
}

class SpiceApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.http })) }.build()
}

val Context.graph: Graph get() = (applicationContext as SpiceApp).graph
