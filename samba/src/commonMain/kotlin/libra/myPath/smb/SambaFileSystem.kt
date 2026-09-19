package libra.myPath.smb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
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
    private lateinit var smbFileSystem: SmbFileSystem

    private fun pathSeparator(path: Path) = if ('\\' in path.path) '\\' else '/'

    suspend fun fileSystem(): SmbFileSystem? = withContext(Dispatchers.IO) {
        if (::smbFileSystem.isInitialized) {
            smbFileSystem
        } else {
            connectSamba(config.toSmbServerConfig())?.also { smbFileSystem = it }
        }
    }

    override suspend fun name(path: Path): String? = path.path.run {
        pathSeparator(path)
            .let { trimEnd(it).substringAfterLast(it, "") }
            .ifBlank { null }
    }

    override suspend fun exists(path: Path): Boolean = withContext(Dispatchers.IO) {
        metadata(path) != null
    }

    override suspend fun metadata(path: Path): FileMetadata? = withContext(Dispatchers.IO) {
        check(fileSystem() != null) { "Failed to connect $config < $path" }

        smbFileSystem.metadata(path.path)?.run {
            FileMetadata(
                isRegularFile = isFile,
                isDirectory = isDirectory,
                size = size.toLong()
            )
        }
    }

    override suspend fun resolveParent(path: Path): Path? = path.path.run {
        pathSeparator(path)
            .let { trimEnd(it).substringBeforeLast(it, "") }
            .ifBlank { null }
            ?.let { Path(it) }
    }

    override suspend fun delete(path: Path): Boolean = withContext(Dispatchers.IO) {
        check(fileSystem() != null) { "Failed to connect $config < $path" }

        if (isTreePath(path) == true) {
            smbFileSystem.deleteDirectory(path.path)
        } else {
            smbFileSystem.deleteFile(path.path)
        }
    }

    override suspend fun file(
        path: Path,
        name: String
    ): FileEntry = FileEntry(
        path = pathSeparator(path).let { sep ->
            Path("${path.path.trimEnd(sep)}$sep$name")
        },
        fileSystem = this
    )

    override suspend fun createFile(
        path: Path,
        name: String
    ): FileEntry = withContext(Dispatchers.IO) {
        file(path, name).apply {
            if (exists()) return@apply

            sink(true).close()
            check(metadata()?.isRegularFile == true) { "Failed to create $this" }
        }
    }

    override suspend fun directory(
        path: Path,
        name: String
    ): DirectoryEntry = DirectoryEntry(
        path = pathSeparator(path).let { sep ->
            Path("${path.path.trimEnd(sep)}$sep$name")
        },
        fileSystem = this
    )


    override suspend fun createDirectory(
        path: Path,
        name: String
    ): DirectoryEntry = withContext(Dispatchers.IO) {
        directory(path, name).apply {
            if (exists()) return@apply
            check(fileSystem() != null) { "Failed to connect $config. < $path, $name" }
            check(smbFileSystem.createDirectory(path.path)) { "Failed to create $this." }
        }
    }

    override fun list(path: Path): Flow<Entry> = flow {
        check(fileSystem() != null) { "Failed to connect $config. < $path" }

        smbFileSystem.listDirectory(path.path)?.forEach {
            if (it.isDirectory) {
                emit(directory(path, it.name))
            } else {
                emit(file(path, it.name))
            }
        }
    }.flowOn(Dispatchers.IO)


    override suspend fun source(path: Path): RawSource = withContext(Dispatchers.IO) {
        check(isTreePath(path) == false) { "$path is not a file or not exists." }
        check(fileSystem() != null) { "Failed to connect $config. < $path" }

        smbFileSystem.openRead(path.path)
            ?.let { SambaSource(path, it) }
            ?: error("Failed to open $path")
    }

    inner class SambaSource(
        val path: Path,
        val reader: SmbFileReader,
        var position: ULong = 0L.toULong()
    ) : RawSource {
        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
            require(byteCount >= 0) { "byteCount < 0: $byteCount < $path" }

            return runBlocking {
                check(fileSystem() != null) { "Failed to connect $config. < $path" }
                reader.readAt(position, byteCount.toULong())?.let {
                    val size = it.size

                    if (size > 0) {
                        sink.write(it)
                        position += size.toULong()
                        size.toLong()
                    } else -1
                } ?: -1
            }
        }

        override fun close() {
            runBlocking { reader.finish() }
            reader.close()
        }
    }

    override suspend fun sink(path: Path, append: Boolean): RawSink = withContext(Dispatchers.IO) {
        check(isTreePath(path) == false) { "$path is not a file or not exists." }
        check(fileSystem() != null) { "Failed to connect $config. < $path" }

        smbFileSystem.openWrite(path.path, append)
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

            return runBlocking {
                check(fileSystem() != null) { "Failed to connect $config. < $path" }
                val buffer = Buffer()
                source.readAtMostTo(buffer, byteCount)

                check(
                    writer.writeAt(buffer.use { it.readByteArray() })
                ) { "Failed to write $path" }
            }
        }
    }
}