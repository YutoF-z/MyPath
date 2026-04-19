package libra.myPath

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer


interface MyFile: MyPath {
    fun toWritableOrNull(): MyRWFile?
    suspend fun source(): RawSource

    suspend fun readString(): String =
        source().buffered().use { it.readString() }

    suspend fun readByteArray(): ByteArray =
        source().buffered().use { it.readByteArray() }

    suspend fun <T> readValue(serializer: KSerializer<T>, format: SerialFormat = json): T = when(format) {
        is BinaryFormat -> format.decodeFromByteArray(serializer, readByteArray())
        is StringFormat -> format.decodeFromString(serializer, readString())
        else -> error("Unsupported format: $format")
    }
}

suspend inline fun <reified T> MyFile.readValue(format: SerialFormat = json): T =
    readValue(serializer(), format)
