package libra.myPath.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import libra.myPath.MyDirectory
import libra.myPath.MyPathInterface
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


    override suspend fun mk(): MyDirectory = apply {
        withContext(Dispatchers.IO) {
            FileSystem.SYSTEM.createDirectories(path, false)
        }
    }


    override suspend fun copyFrom(destination: MyDirectory): MyDirectory = apply {
        if (destination is LocalDirectory) {
            FileSystem.SYSTEM.copy(path, destination.path)
        }
    }

    override suspend fun rm() = FileSystem.SYSTEM.deleteRecursively(path)


    override fun list(pattern: Regex?): Sequence<MyPathInterface> = Sequence {
        FileSystem.SYSTEM.list(path).forEach {
            yield()
        } }

    override fun listRecursively(pattern: Regex?): Sequence<MyPathInterface> {
        TODO("Not yet implemented")
    }

    override suspend fun moveFrom(destination: MyDirectory): MyDirectory = apply {
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