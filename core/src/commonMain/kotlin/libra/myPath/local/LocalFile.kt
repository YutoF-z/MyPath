package libra.myPath.local

import kotlinx.serialization.SerialName
import libra.myPath.MyFile
import libra.myPath.MyPath
import okio.FileMetadata
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
    override suspend fun mv(destination: MyPath): MyPath {
        TODO("Not yet implemented")
    }

    override suspend fun cp(destination: MyPath): MyPath {
        TODO("Not yet implemented")
    }

    override suspend fun rm() {
        TODO("Not yet implemented")
    }

    override suspend fun source(): Source {
        TODO("Not yet implemented")
    }

    override suspend fun sink(): Sink {
        TODO("Not yet implemented")
    }
}