package com.sms.mobile.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sms.mobile.data.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.io.File

/** The upload types the backend accepts, as MIME types for the system file picker. */
val MIME_BY_EXTENSION = mapOf(
    "pdf" to "application/pdf",
    "doc" to "application/msword",
    "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "txt" to "text/plain",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "png" to "image/png",
    "zip" to "application/zip",
)

/** Saves a download into the app's cache and opens it in a viewer app (PDF reader, gallery, ...). */
suspend fun openDownload(context: Context, body: ResponseBody, fileName: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download" }
        File(dir, safeName).also { target ->
            body.use { it.byteStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } } }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
        ?: "application/octet-stream"
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, type)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this phone can open ${file.name}.", Toast.LENGTH_LONG).show()
    }
}

class PickedFile(val name: String, val mimeType: String, val bytes: ByteArray) {
    fun toPart(): MultipartBody.Part =
        MultipartBody.Part.createFormData("file", name, bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
}

/** Reads a file chosen in the system picker, refusing it early if it's over [maxBytes]. */
suspend fun readPickedFile(context: Context, uri: Uri, maxBytes: Long): PickedFile = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    var name = "upload"
    var size = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            if (!cursor.isNull(0)) name = cursor.getString(0)
            if (!cursor.isNull(1)) size = cursor.getLong(1)
        }
    }
    val tooLarge = "File is too large (max ${maxBytes / (1024 * 1024)} MB)."
    if (size > maxBytes) throw ApiException(tooLarge)
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw ApiException("Couldn't read that file.")
    if (bytes.size > maxBytes) throw ApiException(tooLarge)
    PickedFile(name, resolver.getType(uri) ?: "application/octet-stream", bytes)
}
