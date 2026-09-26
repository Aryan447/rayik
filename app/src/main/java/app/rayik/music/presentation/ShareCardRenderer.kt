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
import app.rayik.music.lyrics.LrcParser
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
      LrcParser.parseLyrics(raw)
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
    drawShell(art = art) { canvas, cursorY ->
      var y = cursorY
      y = drawCenteredText(
        canvas = canvas,
        text = lyric.ifBlank { title },
        sizePx = 64f,
        bold = true,
        color = CREAM,
        maxLines = 4,
        y = y,
      )
      y += 28f
      drawCenteredText(
        canvas = canvas,
        text = if (artist.isBlank()) title else "$title • $artist",
        sizePx = 40f,
        bold = false,
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
    drawShell(art = art) { canvas, cursorY ->
      var y = cursorY
      y = drawCenteredText(
        canvas = canvas,
        text = headline,
        sizePx = 96f,
        bold = true,
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
          bold = false,
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

  private fun drawShell(
    art: Bitmap?,
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
      // Note glyph placeholder — never a grey box.
      val note = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GOLD_DIM
        textSize = 320f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
      }
      canvas.drawText("♪", CARD_W / 2f, cursorY + 560f, note)
      cursorY += ART_SIZE + 64f
    }

    content(canvas, cursorY)

    // Footer brand line.
    val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = GOLD_DIM
      textSize = 34f
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("rāyik • Music with opinions.", CARD_W / 2f, CARD_H - 72f, footer)
    return bitmap
  }

  private fun drawCenteredText(
    canvas: Canvas,
    text: String,
    sizePx: Float,
    bold: Boolean,
    color: Int,
    maxLines: Int,
    y: Float,
  ): Float {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      this.color = color
      textSize = sizePx
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
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
