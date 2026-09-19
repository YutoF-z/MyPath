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
import kotlinx.serialization.Serializable
import libra.myPath.DirectoryEntry
import libra.myPath.Entry
import libra.myPath.FileEntry
import libra.myPath.FileSystem
import libra.myPath.Path
import kotlinx.io.files.Path as KPath

@Serializable
object LocalFileSystem : FileSystem {
    override suspend fun name(path: Path): String = KPath(path.path).name
    override suspend fun exists(path: Path): Boolean =
        withContext(Dispatchers.IO) { SystemFileSystem.exists(KPath(path.path)) }

    override suspend fun metadata(path: Path): FileMetadata? =
        withContext(Dispatchers.IO) { SystemFileSystem.metadataOrNull(KPath(path.path)) }

    override suspend fun resolveParent(path: Path): Path? =
        KPath(path.path).parent?.let { Path(it.toString()) }

    override suspend fun delete(path: Path): Boolean = withContext(Dispatchers.IO) {
        SystemFileSystem.delete(KPath(path.path))
        !exists(path)
    }

    override suspend fun source(path: Path): RawSource = withContext(Dispatchers.IO) {
        SystemFileSystem.source(KPath(path.path))
    }

    override suspend fun sink(path: Path, append: Boolean): RawSink = withContext(Dispatchers.IO) {
        SystemFileSystem.sink(KPath(path.path), append)
    }

    override suspend fun moveFrom(path: Path, from: FileEntry): Boolean =
        withContext(Dispatchers.IO) {
            if (from.fileSystem === this) {
                val metadata = from.metadata()
                SystemFileSystem.atomicMove(KPath(from.path.path), KPath(path.path))
                metadata(path)?.size == metadata?.size
            } else {
                super.moveFrom(path, from)
            }
        }

    override suspend fun file(
        path: Path, name: String
    ): FileEntry = withContext(Dispatchers.IO) {
        check(isTreePath(path) == true) { "$path is not a tree path. < $name" }

        FileEntry(
            Path(KPath(KPath(path.path), name).toString()),
            this@LocalFileSystem
        )
    }

    override suspend fun createFile(
        path: Path, name: String
    ): FileEntry = withContext(Dispatchers.IO) {
        file(path, name).run {
            if (metadata()?.isRegularFile == true) {
                return@withContext this
            }

            sink(true).close()
            check(metadata()?.isRegularFile == true) { "Failed to create $this" }
            this
        }
    }

    override suspend fun directory(
        path: Path, name: String
    ): DirectoryEntry = withContext(Dispatchers.IO) {
        check(isTreePath(path) == true) { "$path is not a tree path. < $name" }
        DirectoryEntry(
            Path(KPath(KPath(path.path), name).toString()),
            this@LocalFileSystem
        )
    }

    override suspend fun createDirectory(
        path: Path, name: String
    ): DirectoryEntry = withContext(Dispatchers.IO) {
        directory(path, name).run {
            if (metadata()?.isDirectory == true) {
                return@withContext this
            }

            SystemFileSystem.createDirectories(KPath(path.path))
            check(metadata()?.isDirectory == true) { "Failed to create $this" }
            this
        }
    }

    override fun list(path: Path): Flow<Entry> =
        SystemFileSystem.list(KPath(path.path))
            .asFlow()
            .mapNotNull { p ->
                Path(p.toString()).let {
                    when (isTreePath(it)) {
                        true -> DirectoryEntry(it, this@LocalFileSystem)
                        false -> FileEntry(it, this@LocalFileSystem)
                        null -> null
                    }
                }
            }
            .flowOn(Dispatchers.IO)

    override suspend fun moveFrom(path: Path, from: DirectoryEntry): Boolean =
        withContext(Dispatchers.IO) {
            if (from.fileSystem === this) {
                SystemFileSystem.atomicMove(KPath(from.path.path), KPath(path.path))
                true
            } else {
                super.moveFrom(path, from)
            }
        }
}