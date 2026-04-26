package libra.myPath

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import kotlinx.serialization.serializer
import okio.Sink
import okio.Source
import okio.buffer
import okio.use

@Polymorphic
interface MyFile : MyPath {
    override suspend fun asMyDirectory(mustExist: Boolean): MyDirectory? = null
    override suspend fun asMyFile(mustExist: Boolean): MyFile? = this

    suspend fun source(): Source
    suspend fun sink(): Sink


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
            source().buffer().use { format.decodeFromBufferedSource(serializer, it) }
        }

        else -> format.decodeFromString(serializer, readString())
    }

    suspend fun readByteArray(): ByteArray =
        withContext(Dispatchers.IO) { source().buffer().use { it.readByteArray() } }

    suspend fun readString(): String =
        withContext(Dispatchers.IO) { source().buffer().use { it.readUtf8() } }


    suspend fun <T> write(
        value: T,
        serializer: KSerializer<T>,
        format: BinaryFormat
    ): MyFile = write(
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
                sink().buffer().use { format.encodeToBufferedSink(serializer, value, it) }
            }

            else -> write(format.encodeToString(serializer, value), false)
        }
    }

    suspend fun write(
        value: ByteArray,
        append: Boolean = false
    ): MyFile = apply {
        withContext(Dispatchers.IO) {
            sink().buffer().use { it.write(value) }
        }
    }

    suspend fun write(
        value: String,
        append: Boolean = false
    ): MyFile = apply {
        withContext(Dispatchers.IO) {
            sink().buffer().use { it.writeUtf8(value) }
        }
    }

    suspend fun write(
        source: Source,
        append: Boolean = false
    ): MyFile = apply {
        withContext(Dispatchers.IO) {
            sink().buffer().use { it.writeAll(source) }
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