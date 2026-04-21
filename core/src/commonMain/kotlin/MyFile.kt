package libra.myPath

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.decodeFromSource
import kotlinx.serialization.json.io.encodeToSink
import kotlinx.serialization.serializer


@Polymorphic
interface MyFile : MyPath {
    override suspend fun toMyDirectory(): MyDirectory? = null
    override suspend fun toMyFile(): MyFile? = this

    suspend fun source(): RawSource
    suspend fun sink(append: Boolean = false): RawSink


    suspend fun <T> read(
        serializer: KSerializer<T>,
        format: BinaryFormat
    ): T = format.decodeFromByteArray(serializer, readByteArray())

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun <T> read(
        serializer: KSerializer<T>,
        format: StringFormat
    ): T = when (format) {
        is Json -> withContext(Dispatchers.IO) {
            source().buffered().use { format.decodeFromSource(serializer, it) }
        }

        else -> format.decodeFromString(serializer, readString())
    }

    suspend fun readByteArray(): ByteArray =
        withContext(Dispatchers.IO) { source().buffered().use { it.readByteArray() } }

    suspend fun readString(): String =
        withContext(Dispatchers.IO) { source().buffered().use { it.readString() } }


    suspend fun <T> write(
        value: T,
        serializer: KSerializer<T>,
        format: BinaryFormat
    ): MyFile = writeByteArray(
        format.encodeToByteArray(serializer, value),
        false
    )

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun <T> write(
        value: T,
        serializer: KSerializer<T>,
        format: StringFormat
    ): MyFile = apply {
        when (format) {
            is Json -> withContext(Dispatchers.IO) {
                sink(false).buffered().use { format.encodeToSink(serializer, value, it) }
            }

            else -> writeString(format.encodeToString(serializer, value), false)
        }
    }

    suspend fun writeByteArray(
        value: ByteArray,
        append: Boolean = false
    ): MyFile = apply {
        withContext(Dispatchers.IO) {
            sink(append).buffered().use { it.write(value) }
        }
    }

    suspend fun writeString(
        value: String,
        append: Boolean = false
    ): MyFile = apply {
        withContext(Dispatchers.IO) {
            sink(append).buffered().use { it.writeString(value) }
        }
    }
}

suspend inline fun <reified T> MyFile.read(
    format: BinaryFormat
): T = read(serializer(), format)

suspend inline fun <reified T> MyFile.read(
    format: StringFormat
): T = read(serializer(), format)

suspend inline fun <reified T> MyFile.write(
    value: T,
    format: BinaryFormat
) = write(value, serializer(), format)

suspend inline fun <reified T> MyFile.write(
    value: T,
    format: StringFormat
) = write(value, serializer(), format)