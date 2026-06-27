package libra.myPath.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import libra.myPath.MyDirectory
import libra.myPath.MyFile
import libra.myPath.MyPath
import okio.FileMetadata
import okio.FileSystem

@Serializable
@SerialName("LocalDirectory")
class LocalDirectory(
    override val rawPath: String
) : LocalPath(), MyDirectory {
    constructor(
        rawPath: String,
        metadata: FileMetadata? = null
    ) : this(rawPath) {
        this@LocalDirectory.metadata = metadata
    }

    override fun list(pattern: Regex?): Sequence<MyPath> = Sequence {
        FileSystem.SYSTEM.list(path).forEach {
            yield()
        }
    }

    override fun listRecursively(pattern: Regex?): Sequence<MyPath> {
        FileSystem.SYSTEM.listRecursively(path)
    }

    override fun fileWith(name: String): MyFile = (path / name).toString().toLocalFile()

    override fun dirWith(name: String): MyDirectory = (path / name).toString().toLocalDirectory()


    override suspend fun rm() = withContext(Dispatchers.IO) {
        FileSystem.SYSTEM.deleteRecursively(path)
    }

    override suspend fun mk(): MyDirectory = apply {
        withContext(Dispatchers.IO) {
            FileSystem.SYSTEM.createDirectories(path, false)
        }
    }


    override suspend infix fun moveFrom(destination: MyDirectory): MyDirectory =
        if (destination is LocalDirectory) moveFrom(destination)
        else super moveFrom destination

    suspend infix fun moveFrom(destination: LocalDirectory): LocalDirectory = apply {
        withContext(Dispatchers.IO) {
            FileSystem.SYSTEM.atomicMove(destination.path, path)
        }
    }

    override suspend infix fun copyFrom(destination: MyDirectory): MyDirectory =
        if (destination is LocalDirectory) copyFrom(destination)
        else super copyFrom destination

    suspend fun copyFrom(destination: LocalDirectory): LocalDirectory = apply {
        withContext(Dispatchers.IO) {
            FileSystem.SYSTEM.copy(destination.path, path)
        }
    }

}

expect fun localDirectoryFromDialog(): LocalDirectory
fun String.toLocalDirectory() = LocalDirectory(this)