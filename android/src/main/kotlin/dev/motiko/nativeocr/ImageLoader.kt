package dev.motiko.nativeocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLDecoder

/** A decoded image with its EXIF orientation already applied to the pixels. */
class LoadedImage(val bitmap: Bitmap) {
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height
}

object ImageLoader {
    /**
     * Marker in URLs made by `Capacitor.convertFileSrc()`, e.g.
     * `https://localhost/_capacitor_file_/data/user/0/...`.
     */
    private const val CAPACITOR_FILE_MARKER = "/_capacitor_file_"

    fun load(context: Context, path: String): LoadedImage {
        val bytes = try {
            if (path.startsWith("content:")) {
                context.contentResolver.openInputStream(Uri.parse(path))?.use { it.readBytes() }
                    ?: throw OcrException.InvalidImage("Can't read the image at $path.")
            } else {
                val file = File(fileSystemPath(path))
                if (!file.isFile) throw OcrException.InvalidImage("No file at $path.")
                file.readBytes()
            }
        } catch (error: IOException) {
            throw OcrException.InvalidImage("Can't read the image at $path: ${error.message}")
        } catch (error: SecurityException) {
            throw OcrException.InvalidImage("Can't read the image at $path: ${error.message}")
        }
        return decode(bytes)
    }

    fun load(base64: String): LoadedImage {
        val bytes = try {
            Base64.decode(stripDataUrlPrefix(base64), Base64.DEFAULT)
        } catch (error: IllegalArgumentException) {
            throw OcrException.InvalidImage("The base64 data is not a readable image.")
        }
        return decode(bytes)
    }

    /** Turns a plain path, a `file://` URL or a `convertFileSrc()` URL into a file system path. */
    fun fileSystemPath(path: String): String {
        val marker = path.indexOf(CAPACITOR_FILE_MARKER)
        if (marker >= 0) {
            return percentDecoded(path.substring(marker + CAPACITOR_FILE_MARKER.length))
        }
        if (path.startsWith("file:")) {
            return try {
                URI(path).path ?: path
            } catch (error: Exception) {
                percentDecoded(path.removePrefix("file://"))
            }
        }
        return path
    }

    /** The payload of a `data:` URL, or the string itself. */
    fun stripDataUrlPrefix(base64: String): String {
        val comma = base64.indexOf(',')
        return if (base64.startsWith("data:") && comma >= 0) base64.substring(comma + 1) else base64
    }

    private fun percentDecoded(text: String): String = try {
        // URLDecoder is for forms; keep a literal "+" as it is in a path.
        URLDecoder.decode(text.replace("+", "%2B"), "UTF-8")
    } catch (error: IllegalArgumentException) {
        text
    }

    private fun decode(bytes: ByteArray): LoadedImage {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw OcrException.InvalidImage("The image can't be decoded.")
        val exif = try {
            ExifInterface(ByteArrayInputStream(bytes))
        } catch (error: IOException) {
            null
        }
        return LoadedImage(oriented(bitmap, exif))
    }

    /** Applies the EXIF orientation: turn first, then mirror, which covers all eight values. */
    private fun oriented(bitmap: Bitmap, exif: ExifInterface?): Bitmap {
        val degrees = exif?.rotationDegrees ?: 0
        val flipped = exif?.isFlipped ?: false
        if (degrees == 0 && !flipped) return bitmap
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        if (flipped) matrix.postScale(-1f, 1f)
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (turned !== bitmap) bitmap.recycle()
        return turned
    }
}
