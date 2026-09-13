package libra.myPath.samba

import kotlinx.coroutines.flow.Flow
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.files.FileMetadata
import libra.myPath.DirectoryEntry
import libra.myPath.Entry
import libra.myPath.FileEntry
import libra.myPath.FileSystem
import libra.myPath.Path

expect class SambaFileSystem(server: SambaServer) : FileSystem {
    val server: SambaServer
    override suspend infix fun name(path: Path): String?
    override suspend fun exists(path: Path): Boolean
    override suspend fun metadata(path: Path): FileMetadata?
    override suspend fun resolveParent(path: Path): Path?
    override suspend fun delete(path: Path): Boolean
    override suspend fun source(path: Path): RawSource
    override suspend fun sink(path: Path, append: Boolean): RawSink
    override suspend fun findFile(
        path: Path,
        name: String
    ): FileEntry?

    override suspend fun createFile(
        path: Path,
        name: String
    ): FileEntry?

    override suspend fun findDirectory(
        path: Path,
        name: String
    ): DirectoryEntry?

    override suspend fun createDirectory(
        path: Path,
        name: String
    ): DirectoryEntry?

    override fun list(path: Path): Flow<Entry>
}