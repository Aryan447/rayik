package app.rayik.music.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import app.rayik.music.ui.theme.AppShapes
import app.rayik.music.ui.theme.BrandGradient
import app.rayik.music.ui.theme.RayikIcons
import app.rayik.music.ui.utils.YTThumbQuality
import app.rayik.music.ui.utils.buildYTThumbnailUrl
import app.rayik.music.ui.utils.getNextFallbackUrl
import app.rayik.music.utils.isLocalMediaId
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import timber.log.Timber

/**
 * Public thumbnail needing no auth or cookies — survives CDN 403s where the
 * feed URL fails. Songs/videos only (they own a video id); "" otherwise.
 */
fun publicArtFallback(mediaId: String): String =
  if (mediaId.isBlank() || mediaId.isLocalMediaId()) {
    ""
  } else {
    buildYTThumbnailUrl(mediaId, YTThumbQuality.HQ)
  }

/**
 * Shared artwork tile: five bars on the theme sweep underneath, remote art
 * on top when present. Graceful when art fails — never a grey box.
 *
 * Tries [artworkUrl], then [fallbackUrl] (typically [publicArtFallback]),
 * then each lower ytimg quality rung — some stills only exist at low
 * quality, and the feed CDN 403s where `i.ytimg.com` renders. Failures are
 * logged with their cause so missing art is diagnosable instead of silent
 * gold.
 */
@Composable
fun TrackArt(
  artworkUrl: String,
  modifier: Modifier = Modifier,
  corner: Dp = AppShapes.art,
  fallbackUrl: String = "",
) {
  val context = LocalContext.current
  // ponytail: fixed chain, no retry queue beyond the ytimg quality ladder.
  val chain = remember(artworkUrl, fallbackUrl) { artChain(artworkUrl, fallbackUrl) }
  var attempt by remember(artworkUrl, fallbackUrl) { mutableStateOf(0) }
  val model = chain.getOrElse(attempt) { "" }
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(corner))
      .background(BrandGradient.artSweepBrush()),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = RayikIcons.Raay,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
      modifier = Modifier.fillMaxSize(0.45f),
    )
    Crossfade(
      targetState = model,
      animationSpec = tween(260, easing = FastOutSlowInEasing),
      label = "trackArtworkChange",
    ) { image ->
      if (image.isNotBlank()) {
        val request = remember(image) {
          ImageRequest.Builder(context)
            .data(image)
            // Software bitmaps like the notification loader: hardware bitmaps
            // are the one remaining difference from the only load path that
            // provably renders on-device.
            .allowHardware(false)
            .crossfade(true)
            .listener(
              onError = { _, result ->
                Timber.w(result.throwable, "Artwork load failed: %s", image)
                if (attempt < chain.lastIndex) {
                  attempt += 1
                }
              },
            )
            .build()
        }
        AsyncImage(
          model = request,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.matchParentSize().clip(RoundedCornerShape(corner)),
        )
      }
    }
  }
}

/** Ordered, de-duplicated URLs to try: primary, fallback, ytimg descents. */
private fun artChain(artworkUrl: String, fallbackUrl: String): List<String> {
  val chain = LinkedHashSet<String>()
  if (artworkUrl.isNotBlank()) {
    chain += artworkUrl
    var next = getNextFallbackUrl(artworkUrl)
    while (next != null && chain.add(next)) {
      next = getNextFallbackUrl(next)
    }
  }
  if (fallbackUrl.isNotBlank()) {
    chain += fallbackUrl
    var next = getNextFallbackUrl(fallbackUrl)
    while (next != null && chain.add(next)) {
      next = getNextFallbackUrl(next)
    }
  }
  return chain.toList()
}
