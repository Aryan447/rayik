package app.rayik.music.streaming.piped

import app.rayik.music.preferences.AppearancePreferences
import app.rayik.music.preferences.preference.Preference
import app.rayik.music.preferences.preference.PreferenceStore
import app.rayik.music.streaming.youtube.InnerTubeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PipedParsingTest {
  @Test fun `search maps video items and skips channels`() {
    val body = """
      {"items":[
        {"url":"/watch?v=dQw4w9WgXcQ","title":"Never Gonna Give You Up","uploaderName":"Rick",
         "thumbnail":"https://i.ytimg.com/vi/x/hqdefault.jpg","duration":212,"type":"stream"},
        {"url":"/channel/abc","title":"A Channel","uploaderName":"X","type":"channel"},
        {"url":"/watch?v=short","title":"","uploaderName":"X","type":"stream"}
      ]}
    """.trimIndent()
    val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; explicitNulls = false }
      .decodeFromString(PipedSearchResponse.serializer(), body)
    val tracks = parsed.items.mapNotNull { with(PipedSource) { it.toTrack() } }
    assertEquals(1, tracks.size)
    assertEquals("dQw4w9WgXcQ", tracks[0].id)
    assertEquals("Never Gonna Give You Up", tracks[0].title)
    assertEquals("Rick", tracks[0].artist)
    assertEquals(212_000L, tracks[0].durationMs)
  }

  @Test fun `search tolerates missing optionals`() {
    val body = """{"items":[{"url":"/watch?v=dQw4w9WgXcQ","title":"T"}]}"""
    val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; explicitNulls = false }
      .decodeFromString(PipedSearchResponse.serializer(), body)
    val tracks = parsed.items.mapNotNull { with(PipedSource) { it.toTrack() } }
    assertEquals(1, tracks.size)
    assertEquals("Unknown artist", tracks[0].artist)
    assertEquals(0L, tracks[0].durationMs)
    assertTrue(tracks[0].artworkUrl.isEmpty())
  }

  @Test fun `search rejects unparseable watch urls`() {
    val body = """{"items":[{"url":"/playlist?list=abc","title":"P","type":"playlist"}]}"""
    val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; explicitNulls = false }
      .decodeFromString(PipedSearchResponse.serializer(), body)
    assertNull(with(PipedSource) { parsed.items.first().toTrack() })
  }

  @Test fun `streams response keeps audio renditions and hls fallback`() {
    val body = """
      {"title":"T","uploader":"U","duration":200,"hls":"https://cdn/hls.m3u8",
       "audioStreams":[
         {"url":"https://cdn/a","codec":"opus","format":"webm","bitrate":128000},
         {"url":"https://cdn/b","codec":"mp4a.40.2","format":"m4a","bitrate":48000}
       ],
       "videoStreams":[],"extraField":1}
    """.trimIndent()
    val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; explicitNulls = false }
      .decodeFromString(PipedStreamsResponse.serializer(), body)
    assertEquals(2, parsed.audioStreams.size)
    assertEquals("https://cdn/hls.m3u8", parsed.hls)
    assertEquals("webm", parsed.audioStreams[0].format)
  }

  @Test fun `fallback picks direct audio before manifests`() {
    val response = PipedStreamsResponse(
      hls = "https://cdn/hls",
      dash = "https://cdn/dash",
      audioStreams = listOf(PipedAudioStream(url = "https://cdn/a", codec = "opus", format = "webm", bitrate = 128_000)),
    )
    assertEquals("https://cdn/a", source().pickBest(response)?.url)
  }

  @Test fun `hls fallback carries manifest mime for extension-less urls`() {
    val response = PipedStreamsResponse(hls = "https://manifest.googlevideo.com/api/manifest/hls_variant/xyz")
    val picked = source().pickBest(response)
    assertEquals("https://manifest.googlevideo.com/api/manifest/hls_variant/xyz", picked?.url)
    // Without this ExoPlayer sniffs the manifest as progressive audio
    // and buffers forever — the stuck-Loading bug.
    assertEquals("application/x-mpegURL", picked?.mimeType)
  }

  @Test fun `dash is the last-resort fallback with manifest mime`() {
    val response = PipedStreamsResponse(dash = "https://cdn/dash_manifest")
    val picked = source().pickBest(response)
    assertEquals("https://cdn/dash_manifest", picked?.url)
    assertEquals("application/dash+xml", picked?.mimeType)
  }

  @Test fun `empty streams response picks nothing`() {
    assertNull(source().pickBest(PipedStreamsResponse()))
  }

  @Test fun `excluded urls fail over to the next rendition`() {
    val response = PipedStreamsResponse(
      audioStreams = listOf(
        PipedAudioStream(url = "https://cdn/dead", codec = "opus", format = "webm", bitrate = 160_000),
        PipedAudioStream(url = "https://cdn/live", codec = "mp4a.40.2", format = "m4a", bitrate = 128_000),
      ),
    )
    assertEquals("https://cdn/live", source().pickBest(response, setOf("https://cdn/dead"))?.url)
    assertNull(source().pickBest(response, setOf("https://cdn/dead", "https://cdn/live")))
  }

  private fun source() = PipedSource(
    OkHttpClient(),
    AppearancePreferences(InMemoryPreferenceStore()),
    InnerTubeResolver(OkHttpClient(), ""),
  )

  /** Minimal in-memory store: quality prefs read their defaults. */
  private class InMemoryPreferenceStore : PreferenceStore {
    override fun getString(key: String, defaultValue: String) = pref(key, defaultValue)
    override fun getLong(key: String, defaultValue: Long) = pref(key, defaultValue)
    override fun getInt(key: String, defaultValue: Int) = pref(key, defaultValue)
    override fun getFloat(key: String, defaultValue: Float) = pref(key, defaultValue)
    override fun getBoolean(key: String, defaultValue: Boolean) = pref(key, defaultValue)
    override fun getStringSet(key: String, defaultValue: Set<String>) = pref(key, defaultValue)
    override fun <T> getObject(
      key: String,
      defaultValue: T,
      serializer: (T) -> String,
      deserializer: (String) -> T,
    ) = pref(key, defaultValue)

    override fun getAll(): Map<String, *> = emptyMap<String, Any>()

    private fun <T> pref(key: String, default: T): Preference<T> =
      object : Preference<T> {
        private val flow = MutableStateFlow(default)
        override fun key() = key
        override fun get(): T = flow.value
        override fun set(value: T) { flow.value = value }
        override fun isSet() = false
        override fun delete() { flow.value = default }
        override fun defaultValue(): T = default
        override fun changes(): Flow<T> = flow
        override fun stateIn(scope: CoroutineScope): StateFlow<T> = flow
      }
  }
}
