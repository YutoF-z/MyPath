package libra.myPath

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer

@Serializable
data class FileEntry(override val path: Path, override val fileSystem: FileSystem) : Entry {
    suspend fun delete() = fileSystem.delete(path)
    suspend fun source() = fileSystem.source(path)
    suspend fun sink(append: Boolean = false) = fileSystem.sink(path, append)
    suspend infix fun copyFrom(from: DirectoryEntry) = fileSystem.copyFrom(path, from)
    suspend infix fun moveFrom(from: DirectoryEntry) = fileSystem.moveFrom(path, from)

    suspend fun readByteArray(): ByteArray = withContext(Dispatchers.IO) {
        source().use { it.readByteArray() }
    }

    suspend fun <T> read(
        serializer: KSerializer<T>,
        format: BinaryFormat
    ): T = withContext(Dispatchers.IO) {
        format.decodeFromByteArray(serializer, readByteArray())
    }

    suspend inline infix fun <reified T> read(
        format: BinaryFormat
    ): T = read(serializer<T>(), format)

    suspend fun readString(): String = withContext(Dispatchers.IO) {
        source().use { it.readString() }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun <T> read(
        serializer: KSerializer<T>,
        format: StringFormat
    ): T = withContext(Dispatchers.IO) {
        format.decodeFromString(serializer, readString())
    }

    suspend inline infix fun <reified T> read(
        format: StringFormat
    ): T = read(serializer<T>(), format)


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
        sink(append).use { it.write(value) }
    }


    suspend fun <T> write(
        value: T,
        serializer: KSerializer<T>,
        format: BinaryFormat
    ) = write(
        format.encodeToByteArray(serializer, value),
        false
    )

    suspend inline fun <reified T> write(
        value: T,
        format: BinaryFormat
    ) = write(value, serializer<T>(), format)


    suspend fun write(
        value: String,
        append: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            sink(append).use { it.writeString(value) }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun <T> write(
        value: T,
        serializer: KSerializer<T>,
        format: StringFormat
    ) = write(format.encodeToString(serializer, value), false)

    suspend inline fun <reified T> write(
        value: T,
        format: StringFormat
    ) = write(value, serializer<T>(), format)
}