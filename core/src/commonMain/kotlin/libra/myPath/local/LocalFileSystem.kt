package libra.myPath.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.files.FileMetadata
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.Serializable
import libra.myPath.DirectoryEntry
import libra.myPath.Entry
import libra.myPath.FileEntry
import libra.myPath.FileSystem
import libra.myPath.Path
import kotlinx.io.files.Path as KPath

@Serializable
object LocalFileSystem : FileSystem {
    private fun Path.toKPath(): KPath = KPath(path)

    override suspend fun name(path: Path): String = path.toKPath().name

    override suspend fun exists(path: Path): Boolean =
        withContext(Dispatchers.IO) { SystemFileSystem.exists(path.toKPath()) }

    override suspend fun metadata(path: Path): FileMetadata? =
        withContext(Dispatchers.IO) { SystemFileSystem.metadataOrNull(path.toKPath()) }

    override suspend fun resolveParent(path: Path): Path? =
        path.toKPath().parent?.let { Path(it.toString()) }

    override suspend fun delete(path: Path): Boolean = withContext(Dispatchers.IO) {
        val kPath = path.toKPath()
        SystemFileSystem.delete(kPath, mustExist = false)
        !SystemFileSystem.exists(kPath)
    }

    override suspend fun source(path: Path): RawSource = withContext(Dispatchers.IO) {
        SystemFileSystem.source(path.toKPath())
    }

    override suspend fun sink(path: Path, append: Boolean): RawSink = withContext(Dispatchers.IO) {
        SystemFileSystem.sink(path.toKPath(), append)
    }

    override suspend fun moveFrom(path: Path, from: FileEntry): Boolean =
        withContext(Dispatchers.IO) {
            if (from.fileSystem === this@LocalFileSystem) {
                val fromSize = from.metadata()?.size
                SystemFileSystem.atomicMove(from.path.toKPath(), path.toKPath())
                metadata(path)?.size == fromSize
            } else {
                super.moveFrom(path, from)
            }
        }

    override suspend fun file(
        path: Path, name: String
    ): FileEntry = withContext(Dispatchers.IO) {
        check(isTreePath(path) == true) { "$path is not a tree path. < $name" }
        FileEntry(
            path = Path(KPath(path.toKPath(), name).toString()),
            fileSystem = this@LocalFileSystem,
            parent = path
        )
    }

    override suspend fun createFile(
        path: Path, name: String
    ): FileEntry = withContext(Dispatchers.IO) {
        file(path, name).apply {
            if (metadata()?.isRegularFile == true) return@withContext this
            sink(true).close()
            check(metadata()?.isRegularFile == true) { "Failed to create $this" }
        }
    }

    override suspend fun directory(
        path: Path, name: String
    ): DirectoryEntry = withContext(Dispatchers.IO) {
        check(isTreePath(path) == true) { "$path is not a tree path. < $name" }
        DirectoryEntry(
            path = Path(KPath(path.toKPath(), name).toString()),
            fileSystem = this@LocalFileSystem,
            parent = path
        )
    }

    override suspend fun createDirectory(
        path: Path, name: String
    ): DirectoryEntry = withContext(Dispatchers.IO) {
        directory(path, name).apply {
            if (metadata()?.isDirectory == true) return@withContext this
            SystemFileSystem.createDirectories(this.path.toKPath())
            check(metadata()?.isDirectory == true) { "Failed to create $this" }
        }
    }

    override fun list(path: Path): Flow<Entry> = flow {
        for (childKPath in SystemFileSystem.list(path.toKPath())) {
            val childPath = Path(childKPath.toString())
            val entry = when (SystemFileSystem.metadataOrNull(childKPath)?.isDirectory) {
                true -> DirectoryEntry(childPath, this@LocalFileSystem, path)
                false -> FileEntry(childPath, this@LocalFileSystem, path)
                null -> null
            }
            if (entry != null) emit(entry)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun moveFrom(path: Path, from: DirectoryEntry): Boolean =
        withContext(Dispatchers.IO) {
            if (from.fileSystem === this@LocalFileSystem) {
                SystemFileSystem.atomicMove(from.path.toKPath(), path.toKPath())
                true
            } else {
                super.moveFrom(path, from)
            }
        }
}
