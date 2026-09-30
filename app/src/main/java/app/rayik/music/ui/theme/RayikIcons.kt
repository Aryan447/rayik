package app.rayik.music.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MusicNote
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
import androidx.compose.ui.graphics.vector.PathBuilder
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
  /** Nav: the Raay tab is the five bars, not a house. */
  val Raay: ImageVector by lazy {
    ImageVector.Builder(
      name = "Raay",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).addPath(
      pathData = {
        moveTo(5f, 8f); lineTo(5f, 16f)
        moveTo(8.5f, 5f); lineTo(8.5f, 19f)
        moveTo(12f, 2f); lineTo(12f, 22f)
        moveTo(15.5f, 5f); lineTo(15.5f, 19f)
        moveTo(19f, 8f); lineTo(19f, 16f)
      },
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 1.75f,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round,
    ).build()
  }

  /** Transport play — filled triangle, round joins. */
  val Play: ImageVector by lazy {
    ImageVector.Builder(
      name = "Play",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).addPath(
      pathData = {
        moveTo(8f, 5.5f); lineTo(18.5f, 12f); lineTo(8f, 18.5f); close()
      },
      fill = SolidColor(Color.Black),
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 2f,
      strokeLineJoin = StrokeJoin.Round,
    ).build()
  }

  /** Transport pause — twin bars. */
  val Pause: ImageVector by lazy {
    ImageVector.Builder(
      name = "Pause",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).addPath(
      pathData = {
        moveTo(8.6f, 6.5f); lineTo(8.6f, 17.5f)
        moveTo(15.4f, 6.5f); lineTo(15.4f, 17.5f)
      },
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 3.4f,
      strokeLineCap = StrokeCap.Round,
    ).build()
  }

  /** Transport previous — bar plus filled skip triangle. */
  val Previous: ImageVector by lazy {
    ImageVector.Builder(
      name = "Previous",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).apply {
      addPath(
        pathData = {
          moveTo(16.5f, 6f); lineTo(9.5f, 12f); lineTo(16.5f, 18f); close()
        },
        fill = SolidColor(Color.Black),
      )
      addPath(
        pathData = { moveTo(6.5f, 6f); lineTo(6.5f, 18f) },
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2.2f,
        strokeLineCap = StrokeCap.Round,
      )
    }.build()
  }

  /** Transport next — mirror of previous. */
  val Next: ImageVector by lazy {
    ImageVector.Builder(
      name = "Next",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).apply {
      addPath(
        pathData = {
          moveTo(7.5f, 6f); lineTo(14.5f, 12f); lineTo(7.5f, 18f); close()
        },
        fill = SolidColor(Color.Black),
      )
      addPath(
        pathData = { moveTo(17.5f, 6f); lineTo(17.5f, 18f) },
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2.2f,
        strokeLineCap = StrokeCap.Round,
      )
    }.build()
  }

  private val heartPath: PathBuilder.() -> Unit = {
    moveTo(12f, 20.6f)
    curveTo(6.8f, 16.2f, 3.8f, 13f, 3.8f, 9.4f)
    curveTo(3.8f, 6.9f, 5.8f, 5f, 8.3f, 5f)
    curveTo(9.9f, 5f, 11.2f, 5.9f, 12f, 7.2f)
    curveTo(12.8f, 5.9f, 14.1f, 5f, 15.7f, 5f)
    curveTo(18.2f, 5f, 20.2f, 6.9f, 20.2f, 9.4f)
    curveTo(20.2f, 13f, 17.2f, 16.2f, 12f, 20.6f)
    close()
  }

  /** Action heart — stroke (off state). */
  val Heart: ImageVector by lazy {
    ImageVector.Builder(
      name = "Heart",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).addPath(
      pathData = heartPath,
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 1.75f,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round,
    ).build()
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
      pathData = heartPath,
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

  // TODO(visual-identity): redraw Shuffle/Repeat/RepeatOne in the mark's
  // 1.75-stroke language; they currently keep platform-readable arrows.
}
