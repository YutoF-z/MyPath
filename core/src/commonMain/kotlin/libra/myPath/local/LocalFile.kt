package libra.myPath.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import libra.myPath.MyFile
import okio.FileMetadata
import okio.FileSystem
import okio.Sink
import okio.Source


@Serializable
@SerialName("LocalFile")
class LocalFile(
    override val rawPath: String
) : MyFile, LocalPath() {
    constructor(
        rawPath: String,
        metadata: FileMetadata? = null
    ) : this(rawPath) {
        this@LocalFile.metadata = metadata
    }

    override suspend fun source(): Source = withContext(Dispatchers.IO) {
        FileSystem.SYSTEM.source(path)
    }

    override suspend fun sink(append: Boolean): Sink = withContext(Dispatchers.IO) {
        when (append) {
            true -> FileSystem.SYSTEM.appendingSink(path)
            false -> FileSystem.SYSTEM.sink(path)
        }
    }

    override suspend fun rm() = withContext(Dispatchers.IO) {
        FileSystem.SYSTEM.delete(path)
    }

    override suspend infix fun copyFrom(destination: MyFile): MyFile =
        if (destination is LocalFile) copyFrom(destination)
        else super copyFrom destination

    suspend infix fun copyFrom(destination: LocalFile): MyFile = apply {
        withContext(Dispatchers.IO) { FileSystem.SYSTEM.copy(destination.path, path) }
    }

    override suspend infix fun moveFrom(destination: MyFile): MyFile =
        if (destination is LocalFile) moveFrom(destination)
        else super moveFrom destination

    suspend infix fun moveFrom(destination: LocalFile): LocalFile = apply {
        withContext(Dispatchers.IO) { FileSystem.SYSTEM.atomicMove(destination.path, path) }
    }
}

expect fun localFileFromDialog(): LocalFile
fun String.toLocalFile() = LocalFile(this)