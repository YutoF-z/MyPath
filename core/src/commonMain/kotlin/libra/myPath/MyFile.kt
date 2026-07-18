package libra.myPath

import kotlinx.coroutines.Dispatchers
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
    suspend fun source(): Source
    suspend fun sink(append: Boolean = false): Sink

    suspend infix fun copyFrom(destination: MyFile) {
        write(destination.source())
    }

    suspend infix fun moveFrom(destination: MyFile) {
        runCatching {
            withContext(Dispatchers.IO) {
                copyFrom(destination)
                metadata()?.size
            }
        }.getOrNull()?.run {
            destination.rm()
        }
    }
}


suspend fun MyFile.readByteArray(): ByteArray = withContext(Dispatchers.IO) {
    source().buffer().use { it.readByteArray() }
}

suspend fun <T> MyFile.read(
    serializer: KSerializer<T>,
    format: BinaryFormat
): T = format.decodeFromByteArray(serializer, readByteArray())

suspend inline infix fun <reified T> MyFile.read(
    format: BinaryFormat
): T = read(serializer(), format)


suspend fun MyFile.readString(): String = withContext(Dispatchers.IO) {
    source().buffer().use { it.readUtf8() }
}

@OptIn(ExperimentalSerializationApi::class)
suspend fun <T> MyFile.read(
    serializer: KSerializer<T>,
    format: StringFormat
): T = withContext(Dispatchers.IO) {
    if (format is Json) source().buffer().use {
        format.decodeFromBufferedSource(serializer, it)
    }
    else format.decodeFromString(serializer, readString())
}

suspend inline infix fun <reified T> MyFile.read(
    format: StringFormat
): T = read(serializer(), format)


suspend fun MyFile.write(
    source: Source,
    append: Boolean = false
) {
    withContext(Dispatchers.IO) {
        sink(append).buffer().use { it.writeAll(source) }
    }
}

suspend fun MyFile.write(
    value: ByteArray,
    append: Boolean = false
) {
    withContext(Dispatchers.IO) {
        sink(append).buffer().use { it.write(value) }
    }
}

suspend fun <T> MyFile.write(
    value: T,
    serializer: KSerializer<T>,
    format: BinaryFormat
) = write(
    format.encodeToByteArray(serializer, value),
    false
)

suspend inline fun <reified T> MyFile.write(
    value: T,
    format: BinaryFormat
) = write(value, serializer(), format)


suspend fun MyFile.write(
    value: String,
    append: Boolean = false
) {
    withContext(Dispatchers.IO) {
        sink(append).buffer().use { it.writeUtf8(value) }
    }
}

@OptIn(ExperimentalSerializationApi::class)
suspend fun <T> MyFile.write(
    value: T,
    serializer: KSerializer<T>,
    format: StringFormat
) {
    when (format) {
        is Json -> withContext(Dispatchers.IO) {
            sink().buffer().use { format.encodeToBufferedSink(serializer, value, it) }
        }

        else -> write(format.encodeToString(serializer, value), false)
    }
}

suspend inline fun <reified T> MyFile.write(
    value: T,
    format: StringFormat
) = write(value, serializer(), format)