package com.everettpenne.spice

import com.everettpenne.spice.data.api.ApiResult
import com.everettpenne.spice.data.api.Failure
import com.everettpenne.spice.data.api.JellyfinApi
import com.everettpenne.spice.data.api.MediaUrls
import com.everettpenne.spice.data.auth.Session
import com.everettpenne.spice.data.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class JellyfinApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: JellyfinApi
    private var session: Session? = null

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        session = Session(server.url("/").toString().trimEnd('/'), "u1", "demo", "secret-token", "Home")
        api = JellyfinApi(OkHttpClient(), { session }, { "device-1" })
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun `auth header names the client and carries the token only when there is one`() {
        val signedOut = api.authHeader(token = null)
        assertTrue(signedOut.startsWith("MediaBrowser Client=\"Spice\""))
        assertTrue(signedOut.contains("DeviceId=\"device-1\""))
        assertFalse(signedOut.contains("Token"))
        assertTrue(api.authHeader().contains("Token=\"secret-token\""))
    }

    @Test fun `login posts the credentials without a token and returns the session`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"User":{"Id":"abc","Name":"demo"},"AccessToken":"tok","ServerId":"s"}"""))
        val r = api.login(session!!.serverUrl, "Home", "demo", "pw") as ApiResult.Ok
        assertEquals("tok", r.value.token); assertEquals("abc", r.value.userId)
        val sent = server.takeRequest()
        assertEquals("/Users/AuthenticateByName", sent.path)
        assertFalse(sent.getHeader("Authorization")!!.contains("Token"))
        assertEquals("""{"Username":"demo","Pw":"pw"}""", sent.body.readUtf8())
    }

    @Test fun `a refused login is a bad login, not a signed-out session`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(Failure.BAD_LOGIN, (api.login(session!!.serverUrl, "Home", "demo", "wrong") as ApiResult.Failed).reason)
    }

    @Test fun `a 401 on a normal request means the session ended`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(Failure.SIGNED_OUT, (api.libraries() as ApiResult.Failed).reason)
    }

    @Test fun `requests carry the token in the header and never in the address`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"Items":[],"TotalRecordCount":0}"""))
        api.resume()
        val sent = server.takeRequest()
        assertTrue(sent.getHeader("Authorization")!!.contains("Token=\"secret-token\""))
        assertFalse(sent.path!!.contains("secret-token"))
        assertTrue(sent.path!!.startsWith("/Users/u1/Items/Resume"))
    }

    @Test fun `typed addresses get a scheme, https first`() {
        assertEquals(listOf("https://jf.example.com", "http://jf.example.com"), api.candidates("jf.example.com/"))
        assertEquals(listOf("http://192.168.1.2:8096"), api.candidates(" http://192.168.1.2:8096/ "))
        assertTrue(api.candidates("   ").isEmpty())
    }

    @Test fun `connect accepts a Jellyfin server and rejects something else that answers`() = runBlocking {
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse().setBody("""{"ServerName":"Home","Version":"10.10.0","Id":"x"}"""))
        assertEquals("Home", (api.connect(base) as ApiResult.Ok).value.second.serverName)
        server.enqueue(MockResponse().setBody("""{"hello":"world"}"""))
        assertEquals(Failure.NOT_JELLYFIN, (api.connect(base) as ApiResult.Failed).reason)
        server.enqueue(MockResponse().setBody("<html>router login</html>"))
        assertEquals(Failure.NOT_JELLYFIN, (api.connect(base) as ApiResult.Failed).reason)
    }

    @Test fun `an unreachable server is offline`() = runBlocking {
        val base = server.url("/").toString().trimEnd('/'); server.shutdown()
        assertEquals(Failure.OFFLINE, (api.connect(base) as ApiResult.Failed).reason)
    }

    @Test fun `a real-shaped item list decodes, including fields the app does not know`() {
        val json = """{"Items":[{"Id":"1","Name":"Dune","Type":"Movie","ProductionYear":2021,"RunTimeTicks":93000000000,"CommunityRating":8.1,
            "ImageTags":{"Primary":"abc","Logo":"l"},"BackdropImageTags":["b1"],"Genres":["Sci-Fi"],"SomethingNew":{"x":1},
            "UserData":{"PlaybackPositionTicks":18600000000,"PlayedPercentage":20.0,"Played":false,"IsFavorite":true,"Key":"k"},
            "MediaSources":[{"Id":"ms","Container":"mkv","MediaStreams":[{"Type":"Video","Index":0,"Codec":"hevc"}]}]},
            {"Id":"2","Name":"Pilot","Type":"Episode","SeriesId":"9","SeriesName":"Show","ParentIndexNumber":1,"IndexNumber":1}],"TotalRecordCount":2}"""
        val r = JellyfinJson.decodeFromString<ItemsResult>(json)
        assertEquals(2, r.total)
        val dune = r.items[0]
        assertEquals(155, dune.runtimeMinutes); assertEquals("abc", dune.imageTags.primary); assertTrue(dune.userData.isFavorite)
        assertEquals(0.2f, dune.progress!!, 0.001f)
        assertEquals("mkv", dune.mediaSources[0].container)
        assertEquals("S1 E1", r.items[1].episodeLabel)
    }

    @Test fun `progress is absent when unstarted or finished`() {
        fun item(pos: Long, pct: Double?, played: Boolean = false) = JfItem("i", runTimeTicks = 1000, userData = UserData(pos, pct, played = played))
        assertNull(item(0, null).progress)
        assertNull(item(995, null).progress)
        assertNull(item(0, 100.0).progress)
        assertEquals(.5f, item(500, null).progress!!, 0.001f)
    }

    @Test fun `image and stream addresses are built without the token`() {
        val urls = MediaUrls({ "http://jf.local:8096" }, { "dev" })
        val movie = JfItem("m1", "Dune", "Movie", imageTags = ImageTags(primary = "tag"), backdropTags = listOf("bt"))
        assertEquals("http://jf.local:8096/Items/m1/Images/Primary?fillWidth=400&quality=90&tag=tag", urls.poster(movie))
        assertTrue(urls.backdrop(movie)!!.contains("/Images/Backdrop/0"))
        val direct = urls.directStream("m1", "ms")!!
        assertTrue(direct.contains("/Videos/m1/stream") && direct.contains("static=true") && !direct.contains("api_key") && !direct.contains("Token"))
        assertTrue(urls.hlsStream("m1", "ms", "ps")!!.contains("/Videos/m1/master.m3u8"))
    }

    @Test fun `an episode without its own picture falls back to its series poster`() {
        val urls = MediaUrls({ "http://jf.local" }, { "d" })
        val ep = JfItem("e1", "Pilot", "Episode", seriesId = "s1", seriesPrimaryTag = "st")
        assertTrue(urls.poster(ep)!!.contains("/Items/s1/Images/Primary"))
    }

    @Test fun `nothing is built when signed out`() {
        val urls = MediaUrls({ null }, { "d" })
        assertNull(urls.poster(JfItem("m", imageTags = ImageTags(primary = "t"))))
        assertNull(urls.directStream("m", "ms"))
    }
}
