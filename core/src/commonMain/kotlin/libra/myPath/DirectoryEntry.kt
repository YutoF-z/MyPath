package libra.myPath

import kotlinx.serialization.Serializable


@Serializable
data class DirectoryEntry(override val path: Path, override val fileSystem: FileSystem) : Entry {
    fun file(name: String) = fileSystem.file(path, name)
    fun dir(name: String) = fileSystem.dir(path, name)

    fun list() = fileSystem.list(path)
    fun listRecursively() = fileSystem.listRecursively(path)

    suspend fun mkDir(name: String) = fileSystem.mkDir(path, name)
    suspend fun mkFile(name: String) = fileSystem.mkFile(path, name)
    suspend fun deleteRecursively() = fileSystem.deleteRecursively(path)
    suspend fun copyFrom(from: DirectoryEntry) = fileSystem.copyFrom(path, from)
    suspend fun moveFrom(from: DirectoryEntry) = fileSystem.moveFrom(path, from)
}