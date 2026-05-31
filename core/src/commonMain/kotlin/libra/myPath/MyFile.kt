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
interface MyFile : MyPathInterface {
    suspend fun source(): Source
    suspend fun sink(append: Boolean = false): Sink


    suspend fun copyFrom(destination: MyFile): MyFile = write(destination.source())

    suspend fun moveFrom(destination: MyFile): MyFile = apply {
        copyFrom(destination)
        runCatching { statOrNull() }.getOrNull()?.run {
            destination.rm()
        }
    }

    suspend fun mk(): MyFile? = write(byteArrayOf(), append = true)
}


suspend fun MyFile.copyTo(destination: MyFile): MyFile = destination.copyFrom(this)
suspend fun MyFile.moveTo(destination: MyFile): MyFile = destination.moveFrom(this)

suspend fun <T> MyFile.read(
    serializer: KSerializer<T>,
    format: BinaryFormat
): T = format.decodeFromByteArray(serializer, readByteArray())

@OptIn(ExperimentalSerializationApi::class)
suspend fun <T> MyFile.read(
    serializer: KSerializer<T>,
    format: StringFormat
): T = when (format) {
    is Json -> withContext(Dispatchers.IO) {
        source().buffer().use { format.decodeFromBufferedSource(serializer, it) }
    }

    else -> format.decodeFromString(serializer, readString())
}

suspend inline fun <reified T> MyFile.read(
    format: BinaryFormat
): T = read(serializer(), format)

suspend inline fun <reified T> MyFile.read(
    format: StringFormat
): T = read(serializer(), format)

suspend fun MyFile.readByteArray(): ByteArray =
    withContext(Dispatchers.IO) { source().buffer().use { it.readByteArray() } }

suspend fun MyFile.readString(): String =
    withContext(Dispatchers.IO) { source().buffer().use { it.readUtf8() } }


suspend fun <T> MyFile.write(
    value: T,
    serializer: KSerializer<T>,
    format: BinaryFormat
): MyFile = write(
    format.encodeToByteArray(serializer, value),
    false
)


@OptIn(ExperimentalSerializationApi::class)
suspend fun <T> MyFile.write(
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

suspend inline fun <reified T> MyFile.write(
    value: T,
    format: BinaryFormat
) = write(value, serializer(), format)

suspend inline fun <reified T> MyFile.write(
    value: T,
    format: StringFormat
) = write(value, serializer(), format)

suspend fun MyFile.write(
    value: ByteArray,
    append: Boolean = false
): MyFile = apply {
    withContext(Dispatchers.IO) {
        sink(append).buffer().use { it.write(value) }
    }
}

suspend fun MyFile.write(
    value: String,
    append: Boolean = false
): MyFile = apply {
    withContext(Dispatchers.IO) {
        sink(append).buffer().use { it.writeUtf8(value) }
    }
}

suspend fun MyFile.write(
    source: Source,
    append: Boolean = false
): MyFile = apply {
    withContext(Dispatchers.IO) {
        sink(append).buffer().use { it.writeAll(source) }
    }
}