package libra.myPath.local

import kotlinx.serialization.SerialName
import libra.myPath.MyFile
import libra.myPath.MyPath
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

    override suspend fun asMyDirectory(mustExist: Boolean): LocalDirectory? = null
    override suspend fun asMyFile(mustExist: Boolean): LocalFile = this

    override suspend fun source(): Source = FileSystem.SYSTEM.source(path)

    override suspend fun sink(append: Boolean): Sink = when(append) {
        true -> FileSystem.SYSTEM.appendingSink(path)
        false -> FileSystem.SYSTEM.sink(path)
    }

    override suspend fun rm() = FileSystem.SYSTEM.delete(path)


    override suspend fun mv(destination: MyFile): MyFile = apply {
        if (destination is LocalFile) {
            FileSystem.SYSTEM.atomicMove(path, destination.path)
        } else {
            destination.write(source())
            rm()
        }
    }

    override suspend fun cp(destination: MyFile): MyFile = apply {
        if (destination is LocalFile) {
            FileSystem.SYSTEM.copy(path, destination.path)
        } else {
            destination.write(source())
        }
    }
}