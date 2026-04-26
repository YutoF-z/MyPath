package libra.myPath.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import libra.myPath.MyDirectory
import libra.myPath.MyPath
import okio.FileMetadata

@Serializable
@SerialName("LocalDirectory")
class LocalDirectory(
    override val rawPath: String
) : LocalMyPath(), MyDirectory {
    constructor(path: LocalPath) : this(path.rawPath, path.metadata)
    constructor(
        rawPath: String,
        metadata: FileMetadata? = null
    ) : this(rawPath) {
        this@LocalDirectory.metadata = metadata
    }

    override suspend fun asMyDirectory(mustExist: Boolean): LocalDirectory = this
    override suspend fun asMyFile(mustExist: Boolean): LocalFile? = null
    override suspend fun mv(destination: MyPath): MyPath {
        TODO("Not yet implemented")
    }

    override suspend fun cp(destination: MyPath): MyPath {
        TODO("Not yet implemented")
    }

    override suspend fun rm() {
        TODO("Not yet implemented")
    }

    override fun list(pattern: Regex?): Sequence<MyPath> {
        TODO("Not yet implemented")
    }
}