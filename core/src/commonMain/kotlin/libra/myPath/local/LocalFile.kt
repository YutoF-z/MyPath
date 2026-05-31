package libra.myPath.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import libra.myPath.MyFile
import okio.FileMetadata
import okio.FileSystem
import okio.SYSTEM
import okio.Sink
import okio.Source


@SerialName("LocalFile")
class LocalFile(
    override val rawPath: String
) : MyFile, LocalMyPath() {
    constructor(path: LocalPath) : this(path.rawPath, path.metadata)
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

    override suspend fun rm() = withContext(Dispatchers.IO) { FileSystem.SYSTEM.delete(path) }


    suspend fun moveTo(destination: LocalFile): MyFile = apply {
        withContext(Dispatchers.IO) { FileSystem.SYSTEM.atomicMove(path, destination.path) }
    }

    suspend fun copyTo(destination: LocalFile): MyFile = apply {
        withContext(Dispatchers.IO) { FileSystem.SYSTEM.copy(path, destination.path) }
    }
}