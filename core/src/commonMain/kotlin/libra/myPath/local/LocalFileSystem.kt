package libra.myPath.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.files.FileMetadata
import kotlinx.io.files.SystemFileSystem
import libra.myPath.DirectoryEntry
import libra.myPath.Entry
import libra.myPath.FileEntry
import libra.myPath.FileSystem
import libra.myPath.Path
import kotlinx.io.files.Path as KPath

object LocalFileSystem : FileSystem {
    fun kPath(path: Path) = KPath(path.path)
    private fun KPath.toPath() = Path(toString())

    suspend fun KPath.toFileEntry() =
        if (isTreePath(toPath()) == false) FileEntry(toPath(), this@LocalFileSystem)
        else null

    suspend fun KPath.toDirectoryEntry() =
        if (isTreePath(toPath()) == true) DirectoryEntry(toPath(), this@LocalFileSystem)
        else null

    suspend fun KPath.toEntry() = toPath().let {
        when (isTreePath(it)) {
            true -> DirectoryEntry(it, this@LocalFileSystem)
            false -> FileEntry(it, this@LocalFileSystem)
            null -> null
        }
    }

    override suspend fun name(path: Path): String = kPath(path).name
    override suspend fun exists(path: Path): Boolean =
        withContext(Dispatchers.IO) { SystemFileSystem.exists(kPath(path)) }

    override suspend fun metadata(path: Path): FileMetadata? =
        withContext(Dispatchers.IO) { SystemFileSystem.metadataOrNull(kPath(path)) }

    override suspend fun delete(path: Path): Boolean = withContext(Dispatchers.IO) {
        SystemFileSystem.delete(kPath(path))
        !exists(path)
    }

    override suspend fun source(path: Path): RawSource = withContext(Dispatchers.IO) {
        SystemFileSystem.source(kPath(path))
    }

    override suspend fun sink(path: Path, append: Boolean): RawSink = withContext(Dispatchers.IO) {
        SystemFileSystem.sink(kPath(path), append)
    }

    override suspend fun moveFrom(path: Path, from: FileEntry): Boolean =
        withContext(Dispatchers.IO) {
            if (from.fileSystem === this) {
                val metadata = from.metadata()
                SystemFileSystem.atomicMove(kPath(from.path), kPath(path))
                metadata(path)?.size == metadata?.size
            } else {
                super.moveFrom(path, from)
            }
        }

    override suspend fun findFile(
        path: Path, name: String
    ): FileEntry? = withContext(Dispatchers.IO) {
        check(isTreePath(path) == true) { " < $path, $name" }

        val target = KPath(kPath(path), name)
        if (isTreePath(target.toPath()) == false) target.toFileEntry()
        else null
    }

    override suspend fun createFile(
        path: Path, name: String
    ): FileEntry? = withContext(Dispatchers.IO) {
        check(isTreePath(path) == true) { " < $path, $name" }

        val target = (KPath(kPath(path), name)).toPath()
        if (metadata(target)?.isRegularFile == true) {
            return@withContext FileEntry(target, this@LocalFileSystem)
        }

        sink(target, true).use {}

        if (metadata(target)?.isRegularFile == true) FileEntry(target, this@LocalFileSystem)
        else null
    }

    override suspend fun findDirectory(
        path: Path, name: String
    ): DirectoryEntry? = withContext(Dispatchers.IO) {
        check(isTreePath(path) == true) { " < $path, $name" }

        val target = KPath(kPath(path), name)
        if (metadata(target.toPath())?.isDirectory == true) target.toDirectoryEntry()
        else null
    }

    override suspend fun createDirectory(
        path: Path, name: String
    ): DirectoryEntry? = withContext(Dispatchers.IO) {
        check(isTreePath(path) == true) { " < $path, $name" }
        val target = (KPath(kPath(path), name)).toPath()
        if (metadata(target)?.isDirectory == true) {
            return@withContext DirectoryEntry(target, this@LocalFileSystem)
        }

        SystemFileSystem.createDirectories(kPath(target))
        if (metadata(target)?.isDirectory == true) DirectoryEntry(target, this@LocalFileSystem)
        else null
    }

    override fun list(path: Path): Flow<Entry> =
        SystemFileSystem.list(kPath(path))
            .asFlow()
            .mapNotNull { it.toEntry() }
            .flowOn(Dispatchers.IO)

    override suspend fun moveFrom(path: Path, from: DirectoryEntry): Boolean =
        withContext(Dispatchers.IO) {
            if (from.fileSystem === this) {
                SystemFileSystem.atomicMove(kPath(from.path), kPath(path))
                true
            } else {
                super.moveFrom(path, from)
            }
        }
}