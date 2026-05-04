package libra.myPath.local

import kotlinx.coroutines.yield
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import libra.myPath.MyDirectory
import libra.myPath.MyPath
import okio.FileMetadata
import okio.FileSystem
import okio.SYSTEM

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


    override suspend fun cp(destination: MyDirectory): MyDirectory = apply {
        if (destination is LocalDirectory) {
            FileSystem.SYSTEM.copy(path, destination.path)
        }
    }

    override suspend fun rm() = FileSystem.SYSTEM.deleteRecursively(path)


    override fun list(pattern: Regex?): Sequence<MyPath> = Sequence {
        FileSystem.SYSTEM.list(path).forEach {
            yield()
        } }

    override suspend fun mv(destination: MyDirectory): MyDirectory = apply {
        if (destination is LocalDirectory) {
            FileSystem.SYSTEM.atomicMove(path, destination.path)
        } else {
            list().forEach {
                if (it.metadataOrNull()?.isRegularFile == true) {

                }
            }
        }
    }
}