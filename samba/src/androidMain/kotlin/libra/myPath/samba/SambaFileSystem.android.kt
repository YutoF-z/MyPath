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

actual class SambaFileSystem actual constructor(server: SambaServer) : FileSystem {
    actual val server: SambaServer = server
    actual override suspend fun name(path: Path): String? {
        TODO("Not yet implemented")
    }

    actual override suspend fun exists(path: Path): Boolean {
        TODO("Not yet implemented")
    }

    actual override suspend fun metadata(path: Path): FileMetadata? {
        TODO("Not yet implemented")
    }

    actual override suspend fun resolveParent(path: Path): Path? {
        TODO("Not yet implemented")
    }

    actual override suspend fun delete(path: Path): Boolean {
        TODO("Not yet implemented")
    }

    actual override suspend fun source(path: Path): RawSource {
        TODO("Not yet implemented")
    }

    actual override suspend fun sink(
        path: Path,
        append: Boolean
    ): RawSink {
        TODO("Not yet implemented")
    }

    actual override suspend fun findFile(
        path: Path,
        name: String
    ): FileEntry? {
        TODO("Not yet implemented")
    }

    actual override suspend fun createFile(
        path: Path,
        name: String
    ): FileEntry? {
        TODO("Not yet implemented")
    }

    actual override suspend fun findDirectory(
        path: Path,
        name: String
    ): DirectoryEntry? {
        TODO("Not yet implemented")
    }

    actual override suspend fun createDirectory(
        path: Path,
        name: String
    ): DirectoryEntry? {
        TODO("Not yet implemented")
    }

    actual override fun list(path: Path): Flow<Entry> {
        TODO("Not yet implemented")
    }
}