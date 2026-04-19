package libra.myPath

import kotlinx.io.RawSink
import kotlinx.io.buffered
import kotlinx.io.writeString
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer

interface MyRWFile: MyFile, MyRWPath {
    override fun toWritableOrNull(): MyRWFile = this

    suspend fun sink(append: Boolean = false): RawSink

    suspend fun writeString(
        value: String,
        append: Boolean = false
    ): MyRWFile = apply {
        sink(append).use { it.buffered().writeString(value) }
    }

    suspend fun writeByteArray(
        value: ByteArray,
        append: Boolean = false
    ): MyRWFile = apply {
        sink(append).use { it.buffered().write(value) }
    }

    suspend fun <T> writeValue(
        value: T,
        serializer: KSerializer<T>,
        format: SerialFormat = json,
        append: Boolean = false
    ) = when(format) {
        is BinaryFormat -> writeByteArray(format.encodeToByteArray(serializer, value), append)
        is StringFormat -> writeString(format.encodeToString(serializer, value), append)
        else -> error("Unsupported format: $format")
    }
}

suspend inline fun <reified T> MyRWFile.writeValue(
    value: T,
    format: SerialFormat = json,
    append: Boolean = false
) = writeValue(value, serializer<T>(), format, append)