package libra.myPath

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer

@Serializable
data class FileEntry(override val path: Path, override val fileSystem: FileSystem) : Entry {
    suspend fun delete() = fileSystem delete path
    suspend fun source() = fileSystem source path
    suspend infix fun sink(append: Boolean = false) = fileSystem.sink(path, append)
    suspend infix fun copyFrom(from: FileEntry) = fileSystem.copyFrom(path, from)
    suspend infix fun moveFrom(from: FileEntry) = fileSystem.moveFrom(path, from)


    suspend fun readByteArray(): ByteArray = withContext(Dispatchers.IO) {
        source().buffered().use { it.readByteArray() }
    }

    suspend fun <T> read(
        serializer: KSerializer<T>,
        format: BinaryFormat
    ): T = withContext(Dispatchers.IO) {
        format.decodeFromByteArray(serializer, readByteArray())
    }

    suspend fun readString(): String = withContext(Dispatchers.IO) {
        source().buffered().use { it.readString() }
    }

    suspend fun <T> read(
        serializer: KSerializer<T>,
        format: StringFormat
    ): T = withContext(Dispatchers.IO) {
        format.decodeFromString(serializer, readString())
    }


    suspend fun write(
        source: Source,
        append: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            sink(append).use { source.transferTo(it) > 0 }
        }
    }

    suspend fun write(
        value: ByteArray,
        append: Boolean = false
    ) = withContext(Dispatchers.IO) {
        sink(append).buffered().use { it.write(value) }
    }

    suspend fun write(
        value: String,
        append: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            sink(append).buffered().use { it.writeString(value) }
        }
    }
}


suspend inline infix fun <reified T> FileEntry.read(format: BinaryFormat): T =
    read(serializer<T>(), format)

suspend fun <T> FileEntry.write(
    value: T,
    serializer: KSerializer<T>,
    format: BinaryFormat
) = write(format.encodeToByteArray(serializer, value), false)

suspend inline fun <reified T> FileEntry.write(value: T, format: BinaryFormat) =
    write(value, serializer<T>(), format)


suspend inline infix fun <reified T> FileEntry.read(format: StringFormat): T =
    read(serializer<T>(), format)

suspend fun <T> FileEntry.write(
    value: T,
    serializer: KSerializer<T>,
    format: StringFormat
) = write(format.encodeToString(serializer, value), false)

suspend inline fun <reified T> FileEntry.write(value: T, format: StringFormat) =
    write(value, serializer<T>(), format)