package app.rayik.music.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * rāyik shape scale. Four radii, named — nothing else in `presentation/`.
 *
 * - `art` 18dp: shelf art, quick-tile art, queue art, mark tile, dock art
 * - `card` 22dp: Raay card, settings cards, search field rows, browse tiles
 * - `sheet` 28dp: player top, dock sheet bottom
 * - `pill`: chips, hero play, dock selected tab, progress tracks
 *
 * Exemptions: player hero art stays square full-bleed (0dp), artist avatars
 * stay circles, progress tracks stay pills.
 */
object AppShapes {
  val art = 18.dp
  val card = 22.dp
  val sheet = 28.dp
  val pill = CircleShape

  val artShape = RoundedCornerShape(art)
  val cardShape = RoundedCornerShape(card)
  val sheetShape = RoundedCornerShape(sheet)
}

/** Material3 mapping — uses only the art/card/sheet tokens. */
val AppMaterialShapes =
  Shapes(
    extraSmall = RoundedCornerShape(AppShapes.art),
    small = RoundedCornerShape(AppShapes.art),
    medium = RoundedCornerShape(AppShapes.card),
    large = RoundedCornerShape(AppShapes.card),
    extraLarge = RoundedCornerShape(AppShapes.sheet),
  )
