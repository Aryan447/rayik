package app.rayik.music

import app.rayik.music.streaming.youtube.InnerTubeResolver
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeSearchTest {
  private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

  @Test
  fun `parses duration string correctly`() {
    assertEquals(268_000L, InnerTubeResolver.parseDurationMs("4:28"))
    assertEquals(57_000L, InnerTubeResolver.parseDurationMs("0:57"))
    assertEquals(5_468_000L, InnerTubeResolver.parseDurationMs("1:31:08"))
    assertEquals(0L, InnerTubeResolver.parseDurationMs(""))
    assertEquals(0L, InnerTubeResolver.parseDurationMs(null))
  }

  @Test
  fun `parses compact video renderer items from search response`() {
    val sampleJson = """
    {
      "contents": {
        "sectionListRenderer": {
          "contents": [
            {
              "itemSectionRenderer": {
                "contents": [
                  {
                    "compactVideoRenderer": {
                      "videoId": "BddP6PYo2gs",
                      "title": { "runs": [{ "text": "Kesariya" }] },
                      "shortBylineText": { "runs": [{ "text": "Arijit Singh" }] },
                      "thumbnail": { "thumbnails": [{ "url": "https://img/1.jpg" }, { "url": "https://img/2.jpg" }] },
                      "lengthText": { "runs": [{ "text": "4:28" }] }
                    }
                  },
                  {
                    "compactVideoRenderer": {
                      "videoId": "MJyKN-8UncM",
                      "title": { "runs": [{ "text": "Shayad" }] },
                      "ownerText": { "runs": [{ "text": "Pritam" }] },
                      "thumbnail": { "thumbnails": [{ "url": "https://img/shayad.jpg" }] },
                      "lengthText": { "runs": [{ "text": "3:10" }] }
                    }
                  }
                ]
              }
            }
          ]
        }
      }
    }
    """.trimIndent()

    val element = json.parseToJsonElement(sampleJson)
    val tracks = InnerTubeResolver.parseSearchTracks(element)

    assertEquals(2, tracks.size)
    assertEquals("BddP6PYo2gs", tracks[0].id)
    assertEquals("Kesariya", tracks[0].title)
    assertEquals("Arijit Singh", tracks[0].artist)
    assertEquals("https://img/2.jpg", tracks[0].artworkUrl)
    assertEquals(268_000L, tracks[0].durationMs)

    assertEquals("MJyKN-8UncM", tracks[1].id)
    assertEquals("Shayad", tracks[1].title)
    assertEquals("Pritam", tracks[1].artist)
    assertEquals(190_000L, tracks[1].durationMs)
  }

  @Test
  fun `searchUrl appends apiKey if present`() {
    assertEquals(
      "https://www.youtube.com/youtubei/v1/search?prettyPrint=false",
      InnerTubeResolver.searchUrl(""),
    )
    assertEquals(
      "https://www.youtube.com/youtubei/v1/search?prettyPrint=false&key=xyz",
      InnerTubeResolver.searchUrl("xyz"),
    )
  }
}
