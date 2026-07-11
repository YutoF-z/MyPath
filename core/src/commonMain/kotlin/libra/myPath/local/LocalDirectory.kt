package libra.myPath.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
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

    override fun list(
        contains: String?,
        filter: (MyPath.() -> Boolean)?
    ): Flow<MyPath> = flow {
        for (it in FileSystem.SYSTEM.listRecursively(path)) {
            if (path !in listOf(it.parent, it)) break

            contains?.let { it1 ->
                if (it1 !in it.toString()) continue
            }

            val path = when (FileSystem.SYSTEM.metadata(it).isDirectory) {
                true -> it.toString().toLocalDirectory()
                false -> it.toString().toLocalFile()
            }

            filter?.let { it1 ->
                if (!path.it1()) continue
            }

            emit(path)
        }
    }

    override fun listRecursively(
        contains: String?,
        filter: (MyPath.() -> Boolean)?
    ): Flow<MyPath> = flow {
        for (it in FileSystem.SYSTEM.listRecursively(path)) {
            contains?.let { it1 ->
                if (it1 !in it.toString()) continue
            }

            val path = when (FileSystem.SYSTEM.metadata(it).isDirectory) {
                true -> it.toString().toLocalDirectory()
                false -> it.toString().toLocalFile()
            }

            filter?.let { it1 ->
                if (!path.it1()) continue
            }

            emit(path)
        }
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