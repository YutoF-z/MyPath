package libra.myPath.smb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.files.FileMetadata
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import libra.myPath.DirectoryEntry
import libra.myPath.Entry
import libra.myPath.FileEntry
import libra.myPath.FileSystem
import libra.myPath.Path
import uniffi.samba_cargo.SmbFileReader
import uniffi.samba_cargo.SmbFileSystem
import uniffi.samba_cargo.SmbFileWriter
import uniffi.samba_cargo.connectSamba

@Serializable
class SambaFileSystem(val config: SambaServerConfig) : FileSystem {
    @Transient
    private val mutex = Mutex()

    @Transient
    private var smbFileSystem: SmbFileSystem? = null

    private fun pathSeparator(path: Path): Char = if ('\\' in path.path) '\\' else '/'

    private fun joinPath(parent: Path, name: String): Path {
        val sep = pathSeparator(parent)
        val trimmed = parent.path.trimEnd(sep)
        return if (trimmed.isEmpty()) Path(name) else Path("$trimmed$sep$name")
    }

    suspend fun fileSystem(): SmbFileSystem? =
        smbFileSystem ?: withContext(Dispatchers.IO) {
            mutex.withLock {
                smbFileSystem ?: connectSamba(config.toSmbServerConfig())?.also { smbFileSystem = it }
            }
        }

    private suspend fun requireFs(target: Any): SmbFileSystem =
        checkNotNull(fileSystem()) { "Failed to connect $config < $target" }

    override suspend fun name(path: Path): String? = path.path.run {
        val sep = pathSeparator(path)
        trimEnd(sep).substringAfterLast(sep).ifBlank { null }
    }

    override suspend fun exists(path: Path): Boolean = withContext(Dispatchers.IO) {
        metadata(path) != null
    }

    override suspend fun metadata(path: Path): FileMetadata? = withContext(Dispatchers.IO) {
        requireFs(path).metadata(path.path)?.run {
            FileMetadata(
                isRegularFile = isFile,
                isDirectory = isDirectory,
                size = size.toLong()
            )
        }
    }

    override suspend fun resolveParent(path: Path): Path? = path.path.run {
        val sep = pathSeparator(path)
        trimEnd(sep)
            .substringBeforeLast(sep, "")
            .ifBlank { null }
            ?.let { Path(it) }
    }

    override suspend fun delete(path: Path): Boolean = withContext(Dispatchers.IO) {
        val fs = requireFs(path)
        if (isTreePath(path) == true) {
            fs.deleteDirectory(path.path)
        } else {
            fs.deleteFile(path.path)
        }
    }

    override suspend fun file(
        path: Path,
        name: String
    ): FileEntry = FileEntry(
        path = joinPath(path, name),
        fileSystem = this,
        parent = path
    )

    override suspend fun createFile(
        path: Path,
        name: String
    ): FileEntry = withContext(Dispatchers.IO) {
        file(path, name).apply {
            if (exists()) return@apply

            sink(false).close()
            check(metadata()?.isRegularFile == true) { "Failed to create $this" }
        }
    }

    override suspend fun directory(
        path: Path,
        name: String
    ): DirectoryEntry = DirectoryEntry(
        path = joinPath(path, name),
        fileSystem = this,
        parent = path
    )

    override suspend fun createDirectory(
        path: Path,
        name: String
    ): DirectoryEntry = withContext(Dispatchers.IO) {
        directory(path, name).apply {
            if (exists()) return@apply
            val fs = requireFs(this.path)
            check(fs.createDirectory(this.path.path)) { "Failed to create $this." }
        }
    }

    override fun list(path: Path): Flow<Entry> = flow {
        val fs = requireFs(path)

        fs.listDirectory(path.path)?.forEach { item ->
            if (item.isDirectory) {
                emit(directory(path, item.name))
            } else {
                emit(file(path, item.name))
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun source(path: Path): RawSource = withContext(Dispatchers.IO) {
        check(isTreePath(path) == false) { "$path is not a file or not exists." }
        val fs = requireFs(path)

        fs.openRead(path.path)
            ?.let { SambaSource(path, it) }
            ?: error("Failed to open $path")
    }

    inner class SambaSource(
        val path: Path,
        val reader: SmbFileReader,
        var position: ULong = 0UL
    ) : RawSource {
        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
            require(byteCount >= 0) { "byteCount < 0: $byteCount < $path" }
            if (byteCount == 0L) return 0L

            return runBlocking {
                reader.readAt(position, byteCount.toULong())?.let { bytes ->
                    val size = bytes.size
                    if (size > 0) {
                        sink.write(bytes)
                        position += size.toULong()
                        size.toLong()
                    } else -1L
                } ?: -1L
            }
        }

        override fun close() {
            runBlocking { reader.finish() }
            reader.close()
        }
    }

    override suspend fun sink(path: Path, append: Boolean): RawSink = withContext(Dispatchers.IO) {
        check(isTreePath(path) != true) { "$path is a directory." }
        val fs = requireFs(path)

        fs.openWrite(path.path, append)
            ?.let { SambaSink(path, it) }
            ?: error("Failed to open $path")
    }

    inner class SambaSink(
        val path: Path,
        val writer: SmbFileWriter
    ) : RawSink {
        override fun close() {
            runBlocking { writer.finish() }
            writer.close()
        }

        override fun flush() = Unit

        override fun write(source: Buffer, byteCount: Long) {
            require(byteCount >= 0) { "byteCount < 0: $byteCount < $path" }
            if (byteCount == 0L) return

            val bytes = source.readByteArray(byteCount.toInt())
            runBlocking {
                check(writer.writeAt(bytes)) { "Failed to write $path" }
            }
        }
    }
}
