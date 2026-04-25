package libra.myPath.local

import kotlinx.io.RawSink
import kotlinx.io.RawSource
import okio.FileMetadata
import kotlinx.serialization.SerialName
import libra.myPath.MyFile
import libra.myPath.MyPath


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

    override suspend fun source(): RawSource {
        TODO("Not yet implemented")
    }

    override suspend fun sink(append: Boolean): RawSink {
        TODO("Not yet implemented")
    }
}