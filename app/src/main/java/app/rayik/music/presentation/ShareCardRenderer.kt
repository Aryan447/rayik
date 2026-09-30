package app.rayik.music.presentation

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.rayik.music.R
import app.rayik.music.lyrics.LyricDisplayParser
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val CARD_W = 1080
private const val CARD_H = 1350
private const val ART_SIZE = 840
private const val ART_TOP = 110
private const val ART_RADIUS = 56f
private const val CONTENT_WIDTH = 920

private val GOLD = 0xFFEAC453.toInt()
private val GOLD_DIM = 0xFF9C7F3A.toInt()
private val INK = 0xFF14100A.toInt()
private val CREAM = 0xFFF9F3E7.toInt()

/**
 * Handler for the "share this lyric line as a story card" button. Null when
 * there is nothing worth sharing (no words and no title).
 */
@Composable
fun rememberShareLyricCard(
  raw: String?,
  positionMs: Long,
  title: String,
  artist: String,
  artworkUrl: String,
): (() -> Unit)? {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  val activeLine = remember(raw, positionMs) {
    val lines = if (raw.isNullOrBlank()) {
      emptyList()
    } else {
      LyricDisplayParser.parseTimed(raw)
    }
    lines.getOrNull(activeLyricIndex(lines, positionMs))?.text.orEmpty()
  }
  if (activeLine.isBlank() && title.isBlank()) return null
  return {
    if (!busy) {
      busy = true
      scope.launch(Dispatchers.IO) {
        try {
          val bitmap = ShareCardRenderer.renderLyricCard(
            context = context,
            artworkUrl = artworkUrl,
            lyric = activeLine.ifBlank { title },
            title = title,
            artist = artist,
          )
          withContext(Dispatchers.Main) {
            ShareCardRenderer.shareBitmap(
              context = context,
              bitmap = bitmap,
              text = if (artist.isBlank()) title else "$title — $artist",
            )
          }
        } finally {
          busy = false
        }
      }
    }
  }
}

/**
 * Renders shareable story cards (1080×1350, Gold brand) with android.graphics
 * — no Compose-capture tricks, deterministic on every density. Artwork is
 * fetched through Coil with hardware bitmaps off so it can draw to canvas.
 * The shell stays brand-gold (launcher tile, not the active theme) so a
 * shared image is recognizable as rāyik anywhere; type is Fraunces/Outfit
 * and the placeholder is the five bars.
 */
object ShareCardRenderer {
  suspend fun renderLyricCard(
    context: Context,
    artworkUrl: String,
    lyric: String,
    title: String,
    artist: String,
  ): Bitmap = withContext(Dispatchers.IO) {
    val art = loadArt(context, artworkUrl)
    val fonts = CardFonts.load(context)
    drawShell(art = art, fonts = fonts, footer = context.getString(R.string.share_card_footer)) { canvas, cursorY ->
      var y = cursorY
      y = drawCenteredText(
        canvas = canvas,
        text = lyric.ifBlank { title },
        sizePx = 64f,
        typeface = fonts.serif,
        color = CREAM,
        maxLines = 4,
        y = y,
      )
      y += 28f
      drawCenteredText(
        canvas = canvas,
        text = if (artist.isBlank()) title else "$title • $artist",
        sizePx = 40f,
        typeface = fonts.sans,
        color = GOLD,
        maxLines = 1,
        y = y,
      )
    }
  }

  suspend fun renderWrappedCard(
    context: Context,
    artworkUrl: String,
    headline: String,
    lines: List<String>,
  ): Bitmap = withContext(Dispatchers.IO) {
    val art = loadArt(context, artworkUrl, ART_SIZE / 2)
    val fonts = CardFonts.load(context)
    drawShell(art = art, fonts = fonts, footer = context.getString(R.string.share_card_footer)) { canvas, cursorY ->
      var y = cursorY
      y = drawCenteredText(
        canvas = canvas,
        text = headline,
        sizePx = 96f,
        typeface = fonts.serif,
        color = GOLD,
        maxLines = 2,
        y = y,
      )
      y += 24f
      lines.take(3).forEach { line ->
        y = drawCenteredText(
          canvas = canvas,
          text = line,
          sizePx = 44f,
          typeface = fonts.sans,
          color = CREAM,
          maxLines = 2,
          y = y + 8f,
        )
      }
    }
  }

  fun shareBitmap(context: Context, bitmap: Bitmap, text: String) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    // Prune yesterday's cards so the cache never grows.
    dir.listFiles()?.forEach {
      if (System.currentTimeMillis() - it.lastModified() > 86_400_000L) it.delete()
    }
    val file = File(dir, "rayik-${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    context.startActivity(
      Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
          type = "image/png"
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_TEXT, text)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          clipData = ClipData.newUri(context.contentResolver, "rāyik card", uri)
        },
        null,
      ),
    )
  }

  private suspend fun loadArt(context: Context, url: String, sizePx: Int = ART_SIZE): Bitmap? {
    if (url.isBlank()) return null
    return runCatching {
      val loader = SingletonImageLoader.get(context)
      val result = loader.execute(
        ImageRequest.Builder(context)
          .data(url)
          .size(sizePx)
          .allowHardware(false)
          .build(),
      )
      ((result as? SuccessResult)?.image as? BitmapImage)?.bitmap
    }.getOrNull()
  }

  private data class CardFonts(val serif: Typeface?, val sans: Typeface?) {
    companion object {
      fun load(context: Context): CardFonts = CardFonts(
        serif = runCatching { ResourcesCompat.getFont(context, R.font.fraunces) }.getOrNull(),
        sans = runCatching { ResourcesCompat.getFont(context, R.font.outfit) }.getOrNull(),
      )
    }
  }

  private fun drawShell(
    art: Bitmap?,
    fonts: CardFonts,
    footer: String,
    content: (Canvas, Float) -> Unit,
  ): Bitmap {
    val bitmap = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(INK)

    // Gold hairline frame.
    val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 4f
      color = GOLD_DIM
    }
    canvas.drawRoundRect(RectF(24f, 24f, CARD_W - 24f, CARD_H - 24f), 48f, 48f, frame)

    var cursorY = ART_TOP.toFloat()
    if (art != null) {
      val scaled = Bitmap.createScaledBitmap(art, ART_SIZE, ART_SIZE, true)
      val paint = Paint(Paint.ANTI_ALIAS_FLAG)
      canvas.save()
      val left = (CARD_W - ART_SIZE) / 2f
      canvas.translate(left, cursorY)
      // Rounded-corner clip for the artwork.
      val path = android.graphics.Path().apply {
        addRoundRect(RectF(0f, 0f, ART_SIZE.toFloat(), ART_SIZE.toFloat()), ART_RADIUS, ART_RADIUS, android.graphics.Path.Direction.CW)
      }
      canvas.clipPath(path)
      canvas.drawBitmap(scaled, 0f, 0f, paint)
      canvas.restore()
      if (scaled !== art) scaled.recycle()
      cursorY += ART_SIZE + 64f
    } else {
      // Five gold bars placeholder — never a grey box, never a note glyph.
      val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.LinearGradient(
          0f, cursorY, 0f, cursorY + ART_SIZE,
          intArrayOf(0xFFFFF5D4.toInt(), 0xFFF2CE62.toInt(), 0xFFDEAC33.toInt(), 0xFF9C6E15.toInt()),
          floatArrayOf(0f, 0.3f, 0.7f, 1f),
          android.graphics.Shader.TileMode.CLAMP,
        )
      }
      val unit = 6f
      val barW = 5.5f * unit
      val gap = 4.25f * unit
      val heights = listOf(20f, 34f, 48f, 34f, 20f)
      val totalW = barW * 5 + gap * 4
      var bx = CARD_W / 2f - totalW / 2f
      val cy = cursorY + ART_SIZE / 2f
      heights.forEach { h ->
        val bh = h * unit
        canvas.drawRoundRect(RectF(bx, cy - bh / 2f, bx + barW, cy + bh / 2f), barW / 2f, barW / 2f, barPaint)
        bx += barW + gap
      }
      cursorY += ART_SIZE + 64f
    }

    content(canvas, cursorY)

    // Footer brand line.
    val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = GOLD_DIM
      textSize = 34f
      textAlign = Paint.Align.CENTER
      typeface = fonts.sans ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText(footer, CARD_W / 2f, CARD_H - 72f, footerPaint)
    return bitmap
  }

  private fun drawCenteredText(
    canvas: Canvas,
    text: String,
    sizePx: Float,
    typeface: Typeface?,
    color: Int,
    maxLines: Int,
    y: Float,
  ): Float {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      this.color = color
      textSize = sizePx
      textAlign = Paint.Align.CENTER
      typeface?.let { this.typeface = it }
    }
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_WIDTH)
      .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
      .setMaxLines(maxLines)
      .setEllipsize(android.text.TextUtils.TruncateAt.END)
      .build()
    canvas.save()
    canvas.translate(CARD_W / 2f, y)
    layout.draw(canvas)
    canvas.restore()
    return y + layout.height + 8f
  }
}
