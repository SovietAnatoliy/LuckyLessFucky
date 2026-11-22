package com.luckylessfucky.app.sync

import android.content.Context
import android.net.Uri
import com.luckylessfucky.app.domain.model.ProfileExportPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileImportExportHelper(private val context: Context) {

    suspend fun exportPayloadToUri(uri: Uri, payload: ProfileExportPayload): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val jsonText = ProfileJsonCodec.encodeToJson(payload)
                val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: context.contentResolver.openOutputStream(uri)
                    ?: error("Unable to open output stream for $uri")
                outputStream.use { stream ->
                    stream.write(jsonText.toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
            }
        }

    suspend fun importPayloadFromUri(uri: Uri): Result<ProfileExportPayload> =
        withContext(Dispatchers.IO) {
            runCatching {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: error("Unable to open input stream for $uri")
                val rawJson = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                ProfileJsonCodec.decodeFromJson(rawJson).getOrThrow()
            }
        }

    companion object {
        fun suggestedFileName(username: String): String {
            val safeName = username.trim()
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .ifBlank { "trainer" }
            return "luckylessfucky_${safeName}_profile.json"
        }
    }
}
