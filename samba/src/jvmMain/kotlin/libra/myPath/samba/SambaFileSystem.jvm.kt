package libra.myPath.samba

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
import libra.myPath.DirectoryEntry
import libra.myPath.Entry
import libra.myPath.FileEntry
import libra.myPath.FileSystem
import libra.myPath.Path
import uniffi.samba_cargo.SambaServerConfig
import uniffi.samba_cargo.UniSmbFile
import uniffi.samba_cargo.UniSmbFileSystem
import uniffi.samba_cargo.connectSamba

actual class SambaFileSystem actual constructor(server: SambaServer) : FileSystem {
    actual val server: SambaServer = server

    @Volatile
    private var impl: UniSmbFileSystem? = null
    private val connectMutex = Mutex()

    private suspend fun getImpl(forceReconnect: Boolean = false): UniSmbFileSystem =
        withContext(Dispatchers.IO) {
            if (forceReconnect) {
                connectMutex.withLock {
                    impl = null
                }
            }
            impl ?: connectMutex.withLock {
                impl ?: run {
                    val config = SambaServerConfig(
                        host = server.host,
                        port = server.port?.toUShort(),
                        shareName = server.shareName,
                        username = server.username,
                        password = server.password,
                        domain = server.domain
                    )
                    connectSamba(config)
                        ?.also { impl = it }
                        ?: throw IllegalStateException("Failed to connect SMB server with hosts: ${server.host}")
                }
            }
        }

    private suspend fun <T> withRetry(block: suspend (UniSmbFileSystem) -> T?): T? {
        val currentImpl = getImpl()
        val result = block(currentImpl)
        if (result != null) return result

        val newImpl = getImpl(forceReconnect = true)
        return block(newImpl)
    }

    actual override suspend fun name(path: Path): String? {
        val clean = path.path.replace('\\', '/').trimEnd('/')
        if (clean.isBlank()) return "/"
        return clean.substringAfterLast('/')
    }

    actual override suspend fun exists(path: Path): Boolean = metadata(path) != null

    actual override suspend fun metadata(path: Path): FileMetadata? = withContext(Dispatchers.IO) {
        val meta = withRetry { it.metadata(normalizePath(path.path)) } ?: return@withContext null
        FileMetadata(
            isRegularFile = meta.isFile,
            isDirectory = meta.isDirectory,
            size = meta.size.toLong()
        )
    }

    actual override suspend fun resolveParent(path: Path): Path? {
        val clean = path.path.replace('\\', '/').trimEnd('/')
        val lastIndex = clean.lastIndexOf('/')
        if (lastIndex <= 0) return null
        return Path(clean.substring(0, lastIndex))
    }

    actual override suspend fun delete(path: Path): Boolean = withContext(Dispatchers.IO) {
        val normPath = normalizePath(path.path)
        val meta = metadata(path) ?: return@withContext false
        withRetry { fs ->
            if (meta.isDirectory) fs.deleteDirectory(normPath) else fs.deleteFile(normPath)
        } ?: false
    }

    actual override suspend fun source(path: Path): RawSource = withContext(Dispatchers.IO) {
        val normPath = normalizePath(path.path)
        val file = withRetry { it.openRead(normPath) }
            ?: throw IllegalStateException("Failed to open file for reading: $normPath")
        SambaRawSource(file)
    }

    actual override suspend fun sink(path: Path, append: Boolean): RawSink =
        withContext(Dispatchers.IO) {
            val normPath = normalizePath(path.path)
            val file = withRetry { it.openWrite(normPath, append) }
                ?: throw IllegalStateException("Failed to open file for writing: $normPath")

            val initialOffset = if (append) {
                metadata(path)?.size ?: 0L
            } else 0L

            SambaRawSink(file, initialOffset)
        }

    actual override suspend fun findFile(path: Path, name: String): FileEntry? =
        withContext(Dispatchers.IO) {
            val childPathStr = joinPath(path.path, name)
            val meta = metadata(Path(childPathStr)) ?: return@withContext null
            if (meta.isRegularFile) FileEntry(
                Path(childPathStr),
                this@SambaFileSystem,
                path
            ) else null
        }

    actual override suspend fun createFile(path: Path, name: String): FileEntry? =
        withContext(Dispatchers.IO) {
            val childPathStr = joinPath(path.path, name)
            val existing = findFile(path, name)
            if (existing != null) return@withContext existing

            val file = withRetry { it.openWrite(normalizePath(childPathStr), append = false) }
            if (file != null) {
                file.use { /* Opens and immediately closes the file via use block */ }
                FileEntry(Path(childPathStr), this@SambaFileSystem, path)
            } else null
        }

    actual override suspend fun findDirectory(path: Path, name: String): DirectoryEntry? =
        withContext(Dispatchers.IO) {
            val childPathStr = joinPath(path.path, name)
            val meta = metadata(Path(childPathStr)) ?: return@withContext null
            if (meta.isDirectory) DirectoryEntry(
                Path(childPathStr),
                this@SambaFileSystem,
                path
            ) else null
        }

    actual override suspend fun createDirectory(path: Path, name: String): DirectoryEntry? =
        withContext(Dispatchers.IO) {
            val childPathStr = joinPath(path.path, name)
            val existing = findDirectory(path, name)
            if (existing != null) return@withContext existing

            val success = withRetry { it.createDirectory(normalizePath(childPathStr)) } ?: false
            if (success) {
                DirectoryEntry(Path(childPathStr), this@SambaFileSystem, path)
            } else null
        }

    actual override fun list(path: Path): Flow<Entry> = flow {
        val list = withRetry { it.listDirectory(normalizePath(path.path)) } ?: emptyList()
        for (item in list) {
            val childPath = Path(joinPath(path.path, item.name))
            if (item.isDirectory) {
                emit(DirectoryEntry(childPath, this@SambaFileSystem, path))
            } else {
                emit(FileEntry(childPath, this@SambaFileSystem, path))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun normalizePath(rawPath: String): String {
        return rawPath.replace('\\', '/').trim('/')
    }

    private fun joinPath(parent: String, child: String): String {
        val p = normalizePath(parent)
        val c = normalizePath(child)

        return when {
            p.isEmpty() -> c
            c.isEmpty() -> p
            else -> "$p/$c"
        }
    }

    // --- Inner Classes for Streams ---

    private class SambaRawSource(
        private val file: UniSmbFile
    ) : RawSource {
        private var offset = 0L
        private var isClosed = false

        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
            check(!isClosed) { "Source is closed" }
            if (byteCount == 0L) return 0L

            val fetchSize = byteCount.coerceAtMost(MAX_CHUNK_SIZE).toUInt()
            val bytes = runBlocking(Dispatchers.IO) {
                file.readAt(offset.toULong(), fetchSize)
            } ?: return -1L

            if (bytes.isEmpty()) return -1L

            sink.write(bytes)
            offset += bytes.size
            return bytes.size.toLong()
        }

        override fun close() {
            if (!isClosed) {
                isClosed = true
                runBlocking(Dispatchers.IO) {
                    file.close()
                }
            }
        }
    }

    private class SambaRawSink(
        private val file: UniSmbFile,
        initialOffset: Long
    ) : RawSink {
        private var offset = initialOffset
        private var isClosed = false

        override fun write(source: Buffer, byteCount: Long) {
            check(!isClosed) { "Sink is closed" }
            var remaining = byteCount

            while (remaining > 0) {
                val chunkSize = remaining.coerceAtMost(MAX_CHUNK_SIZE).toInt()
                val chunk = source.readByteArray(chunkSize)

                val written = runBlocking(Dispatchers.IO) {
                    file.writeAt(offset.toULong(), chunk)
                } ?: throw IllegalStateException("Failed to write chunk at offset $offset")

                offset += written.toLong()
                remaining -= written.toLong()
            }
        }

        override fun flush() {}

        override fun close() {
            if (!isClosed) {
                isClosed = true
                runBlocking(Dispatchers.IO) {
                    file.close()
                }
            }
        }
    }

    companion object {
        private const val MAX_CHUNK_SIZE = 512L * 1024L
    }
}