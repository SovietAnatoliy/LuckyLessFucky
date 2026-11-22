package com.luckylessfucky.app.sync

import com.luckylessfucky.app.domain.model.ProfileExportPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

object ProfileJsonCodec {
    private const val MAGIC_HEADER = 0x4C4C4631 // "LLF1"
    private const val MAX_PAYLOAD_BYTES = 5 * 1024 * 1024 // 5 MB safety cap

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeToJson(payload: ProfileExportPayload): String {
        return json.encodeToString(payload)
    }

    fun decodeFromJson(rawJson: String): Result<ProfileExportPayload> {
        return runCatching {
            val parsed = json.decodeFromString<ProfileExportPayload>(rawJson.trim())
            require(parsed.username.isNotBlank()) { "Profile username cannot be blank" }
            val normalizedId = parsed.profileId.ifBlank { UUID.randomUUID().toString() }
            parsed.copy(
                profileId = normalizedId,
                username = parsed.username.trim(),
                recordedVariantIds = parsed.recordedVariantIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            )
        }
    }

    /**
     * Writes a length-prefixed, SHA-256 checksummed profile frame over an OutputStream (used by Bluetooth RFCOMM).
     */
    fun writeFramedPayload(payload: ProfileExportPayload, outputStream: OutputStream) {
        val jsonBytes = encodeToJson(payload).toByteArray(Charsets.UTF_8)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(jsonBytes)
        val dataOut = DataOutputStream(outputStream)
        dataOut.writeInt(MAGIC_HEADER)
        dataOut.writeInt(jsonBytes.size)
        dataOut.write(sha256)
        dataOut.write(jsonBytes)
        dataOut.flush()
    }

    /**
     * Reads and verifies a length-prefixed, SHA-256 checksummed profile frame from an InputStream.
     */
    fun readFramedPayload(inputStream: InputStream): Result<ProfileExportPayload> {
        return runCatching {
            val dataIn = DataInputStream(inputStream)
            val magic = dataIn.readInt()
            require(magic == MAGIC_HEADER) {
                "Invalid Bluetooth frame header: expected LLF1"
            }
            val length = dataIn.readInt()
            require(length in 1..MAX_PAYLOAD_BYTES) {
                "Invalid payload size: $length bytes"
            }
            val expectedHash = ByteArray(32)
            dataIn.readFully(expectedHash)
            val payloadBytes = ByteArray(length)
            dataIn.readFully(payloadBytes)

            val actualHash = MessageDigest.getInstance("SHA-256").digest(payloadBytes)
            require(expectedHash.contentEquals(actualHash)) {
                "Bluetooth transfer checksum mismatch"
            }

            val rawJson = String(payloadBytes, Charsets.UTF_8)
            decodeFromJson(rawJson).getOrThrow()
        }
    }
}
