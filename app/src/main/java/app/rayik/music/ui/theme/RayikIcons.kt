package app.rayik.music.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * rāyik icon set: the five gold bars' language — 24dp grid, 1.75 stroke,
 * round caps, no fill except the play triangle and the heart-on state.
 * Tint with `onSurface` / `primary`.
 *
 * Brand-critical glyphs are drawn here (Raay bars, transport, heart).
 * Everything else aliases the single Material *outlined* weight so no
 * screen mixes filled glyphs with strokes. Shuffle/repeat keep their
 * platform-readable outlined arrows as an interim; redrawing them in the
 * mark language is a follow-up, not this pass.
 */
object RayikIcons {
  private fun strokeIcon(name: String, d: String, width: Float = 1.75f): ImageVector =
    ImageVector.Builder(
      name = name,
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).addPath(
      pathData = PathParser().parsePathString(d).toNodes(),
      stroke = SolidColor(Color.Black),
      strokeLineWidth = width,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round,
    ).build()

  private fun fillIcon(name: String, d: String): ImageVector =
    ImageVector.Builder(
      name = name,
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).addPath(
      pathData = PathParser().parsePathString(d).toNodes(),
      fill = SolidColor(Color.Black),
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 2f,
      strokeLineJoin = StrokeJoin.Round,
    ).build()

  /** Nav: the Raay tab is the five bars, not a house. */
  val Raay: ImageVector by lazy {
    strokeIcon("Raay", "M5,8 V16 M8.5,5 V19 M12,2 V22 M15.5,5 V19 M19,8 V16")
  }

  /** Transport play — filled triangle, round joins. */
  val Play: ImageVector by lazy {
    fillIcon("Play", "M8,5.5 L18.5,12 L8,18.5 Z")
  }

  /** Transport pause — twin bars. */
  val Pause: ImageVector by lazy {
    strokeIcon("Pause", "M8.6,6.5 V17.5 M15.4,6.5 V17.5", width = 3.4f)
  }

  /** Transport previous — bar plus filled skip triangle. */
  val Previous: ImageVector by lazy {
    ImageVector.Builder(
      name = "Previous",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).addPath(
      pathData = PathParser().parsePathString("M16.5,6 L9.5,12 L16.5,18 Z").toNodes(),
      fill = SolidColor(Color.Black),
    ).addPath(
      pathData = PathParser().parsePathString("M6.5,6 V18").toNodes(),
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 2.2f,
      strokeLineCap = StrokeCap.Round,
    ).build()
  }

  /** Transport next — mirror of previous. */
  val Next: ImageVector by lazy {
    ImageVector.Builder(
      name = "Next",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).addPath(
      pathData = PathParser().parsePathString("M7.5,6 L14.5,12 L7.5,18 Z").toNodes(),
      fill = SolidColor(Color.Black),
    ).addPath(
      pathData = PathParser().parsePathString("M17.5,6 V18").toNodes(),
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 2.2f,
      strokeLineCap = StrokeCap.Round,
    ).build()
  }

  private const val HEART =
    "M12,20.6 " +
      "C6.8,16.2 3.8,13 3.8,9.4 " +
      "C3.8,6.9 5.8,5 8.3,5 " +
      "C9.9,5 11.2,5.9 12,7.2 " +
      "C12.8,5.9 14.1,5 15.7,5 " +
      "C18.2,5 20.2,6.9 20.2,9.4 " +
      "C20.2,13 17.2,16.2 12,20.6 Z"

  /** Action heart — stroke (off state). */
  val Heart: ImageVector by lazy {
    strokeIcon("Heart", HEART)
  }

  /** Action heart — filled (on state). The one sanctioned fill besides play. */
  val HeartFilled: ImageVector by lazy {
    ImageVector.Builder(
      name = "HeartFilled",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).addPath(
      pathData = PathParser().parsePathString(HEART).toNodes(),
      fill = SolidColor(Color.Black),
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 1f,
      strokeLineJoin = StrokeJoin.Round,
    ).build()
  }

  // Single outlined weight for everything else — never Icons.Filled.
  val SearchNav = Icons.Outlined.Search
  val LibraryNav = Icons.Outlined.LibraryMusic
  val SettingsNav = Icons.Outlined.Settings
  val Close = Icons.Outlined.Close
  val Share = Icons.Outlined.Share
  val Queue = Icons.Outlined.QueueMusic
  val Lyrics = Icons.Outlined.Lyrics
  val Shuffle = Icons.Outlined.Shuffle
  val Repeat = Icons.Outlined.Repeat
  val RepeatOne = Icons.Outlined.RepeatOne
  val History = Icons.Outlined.History
  val MusicNote = Icons.Outlined.MusicNote
  val Resync = Icons.Outlined.MyLocation

  // TODO(visual-identity): redraw Shuffle/Repeat/RepeatOne in the mark's
  // 1.75-stroke language; they currently keep platform-readable arrows.
}
