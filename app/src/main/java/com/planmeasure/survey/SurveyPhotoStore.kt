package com.planmeasure.survey

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Directory of survey documents, relative to the app files directory. */
const val SURVEY_DATA_DIRECTORY = "surveys"

/** Directory of survey photos, relative to the app files directory and visible to FileProvider. */
const val SURVEY_PHOTOS_DIRECTORY = "surveys/photos"

/** File of the photo that belongs to the survey with [id]; the name is derived, not stored. */
fun surveyPhotoFile(directory: File, id: String): File = File(directory, "$id.jpg")

/** Photo directory under the app files directory, used by screens that only read a thumbnail. */
fun surveyPhotoDirectory(context: Context): File =
    File(context.filesDir, SURVEY_PHOTOS_DIRECTORY)

/**
 * Stores the single photo of a survey next to its document.
 *
 * A photo is copied into app storage instead of being referenced by a gallery URI: the URI can
 * expire, the user can revoke access, and the sketch would then drift away from the picture it was
 * drawn on. On import the photo is normalized once: EXIF rotation is applied and the long side is
 * reduced, so showing it needs no orientation handling and a whole photo fits into a phone's
 * memory (ADR 0022).
 *
 * The camera app writes to a temporary `<id>.camera.jpg`; it becomes `<id>.jpg` only after the
 * successful import, so a cancelled capture cannot destroy the previous photo.
 */
class SurveyPhotoStore(context: Context, private val directory: File) {

    private val resolver = context.applicationContext.contentResolver

    fun prepare() {
        directory.mkdirs()
    }

    fun fileFor(id: String): File = surveyPhotoFile(directory, id)

    /** Target the system camera app writes to; declared for FileProvider in `file_paths.xml`. */
    fun cameraTargetFor(id: String): File = File(directory, "$id.camera.jpg")

    /**
     * Creates an empty camera target before the capture intent is launched.
     *
     * Some camera apps open `EXTRA_OUTPUT` with `"rw"`, which fails when the file does not exist
     * yet, and the capture then silently produces nothing. An empty file makes the target
     * reachable for both modes; the camera truncates and overwrites it.
     */
    fun prepareCameraTarget(id: String): File {
        prepare()
        val target = cameraTargetFor(id)
        target.delete()
        runCatching { target.createNewFile() }
        if (!target.isFile) {
            Log.w(TAG, "Could not create camera target at ${target.absolutePath}")
        }
        return target
    }

    fun hasPhoto(id: String): Boolean = fileFor(id).isFile

    fun delete(id: String) {
        fileFor(id).delete()
        cameraTargetFor(id).delete()
    }

    /** Copies a gallery image into app storage, normalizing orientation and size. */
    suspend fun importFromUri(
        surveyId: String,
        uri: Uri,
        source: SurveyPhotoSource,
    ): SurveyPhoto? = withContext(Dispatchers.IO) {
        val bitmap = decodeNormalized(uri) ?: return@withContext null
        save(surveyId, bitmap, source)
    }

    /** Normalizes the photo the camera app left in [cameraTargetFor] and promotes it to the photo. */
    suspend fun importFromCamera(surveyId: String): SurveyPhoto? = withContext(Dispatchers.IO) {
        val temp = cameraTargetFor(surveyId)
        // The target exists but is empty: it was created before the capture intent. A camera app
        // may return before it has finished writing, so give it a short moment to fill the file.
        val length = awaitCameraFile(temp)
        if (length <= 0L) {
            Log.w(
                TAG,
                "Camera left no usable file at ${temp.absolutePath} " +
                    "(exists=${temp.isFile}, length=$length)",
            )
            temp.delete()
            return@withContext null
        }
        try {
            val bitmap = decodeNormalized(Uri.fromFile(temp))
            if (bitmap == null) {
                Log.w(TAG, "Camera file is not decodable: $length bytes")
                return@withContext null
            }
            val photo = save(surveyId, bitmap, SurveyPhotoSource.CAMERA)
            if (photo == null) Log.w(TAG, "Camera photo could not be stored")
            photo
        } finally {
            temp.delete()
        }
    }

    private suspend fun awaitCameraFile(file: File): Long {
        var length = file.length()
        repeat(CAMERA_FILE_ATTEMPTS) {
            if (length > 0L) return length
            delay(CAMERA_FILE_DELAY_MS)
            length = file.length()
        }
        return length
    }

    fun discardCameraTarget(surveyId: String) {
        cameraTargetFor(surveyId).delete()
    }

    /**
     * Decodes the stored photo with a sample that keeps its long side within [maxSidePx].
     *
     * The editor asks for the stored resolution, the list for a thumbnail, so the same file serves
     * both without a second copy on disk.
     */
    suspend fun load(surveyId: String, maxSidePx: Int = DISPLAY_MAX_SIDE): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = fileFor(surveyId)
            if (!file.isFile) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSidePx)
            }
            BitmapFactory.decodeFile(file.path, options)
        }

    private fun save(surveyId: String, bitmap: Bitmap, source: SurveyPhotoSource): SurveyPhoto? {
        directory.mkdirs()
        val target = fileFor(surveyId)
        val temp = File(directory, "$surveyId.jpg.tmp")
        val written = runCatching {
            FileOutputStream(temp).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream))
                runCatching { stream.fd.sync() }
            }
        }
        written.exceptionOrNull()?.let { Log.w(TAG, "Could not write survey photo", it) }
        if (written.isFailure) {
            temp.delete()
            return null
        }
        val replaced = temp.renameTo(target) || run {
            target.delete()
            temp.renameTo(target)
        }
        if (!replaced) {
            Log.w(TAG, "Could not move survey photo into ${target.absolutePath}")
            temp.delete()
            return null
        }
        return SurveyPhoto(
            width = bitmap.width,
            height = bitmap.height,
            source = source,
            importedAt = System.currentTimeMillis(),
        )
    }

    /**
     * Decodes [uri], applies EXIF orientation and scales the result down to [MAX_STORED_SIDE].
     *
     * The bitmap is decoded with a sample first, so a modern 12-megapixel photo never becomes a
     * full-size bitmap in memory just to be reduced.
     */
    private fun decodeNormalized(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = open(uri)
        if (boundsStream == null) {
            Log.w(TAG, "Could not open image $uri")
            return null
        }
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "Could not read image bounds of $uri")
            return null
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_DECODE_SIDE)
        }
        var bitmap = open(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (bitmap == null) {
            Log.w(TAG, "Could not decode image $uri (${bounds.outWidth}x${bounds.outHeight})")
            return null
        }

        val orientation = open(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrNull()
        } ?: ExifInterface.ORIENTATION_NORMAL
        val oriented = orient(bitmap, orientation)
        if (oriented !== bitmap) {
            bitmap.recycle()
            bitmap = oriented
        }

        val scaled = scaleDown(bitmap, MAX_STORED_SIDE)
        if (scaled !== bitmap) {
            bitmap.recycle()
            bitmap = scaled
        }
        return bitmap
    }

    private fun open(uri: Uri) = runCatching { resolver.openInputStream(uri) }.getOrNull()

    private fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longest
        val width = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return runCatching { Bitmap.createScaledBitmap(bitmap, width, height, true) }
            .getOrDefault(bitmap)
    }

    private fun sampleSizeFor(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        val longest = max(width, height)
        while (longest / sample > maxSide) sample *= 2
        return sample
    }

    private companion object {
        const val TAG = "SurveyPhoto"

        /** Long side of the photo kept in app storage. */
        const val MAX_STORED_SIDE = 2048

        /** Long side the decoder is allowed to produce before the final scale-down. */
        const val MAX_DECODE_SIDE = 3072

        const val JPEG_QUALITY = 85

        /** How long to wait for a camera app that returns before its file is complete. */
        const val CAMERA_FILE_ATTEMPTS = 12
        const val CAMERA_FILE_DELAY_MS = 100L

        /** Default decoding size of the editor. */
        const val DISPLAY_MAX_SIDE = 2048
    }
}
