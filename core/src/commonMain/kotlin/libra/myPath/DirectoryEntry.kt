package libra.myPath

import kotlinx.serialization.Serializable


@Serializable
data class DirectoryEntry(override val path: Path, override val fileSystem: FileSystem) : Entry {
    suspend infix fun findFile(name: String) = fileSystem.findFile(path, name)
    suspend infix fun createFile(name: String) = fileSystem.createFile(path, name)
    suspend infix fun findOrCreateFile(name: String) = fileSystem.findOrCreateFile(path, name)

    suspend infix fun findDirectory(name: String) = fileSystem.findDirectory(path, name)
    suspend infix fun createDirectory(name: String) = fileSystem.createDirectory(path, name)
    suspend infix fun findOrCreateDirectory(name: String) =
        fileSystem.findOrCreateDirectory(path, name)

    fun list() = fileSystem list path
    fun listRecursively(maxDepth: Int = 100) = fileSystem.listRecursively(path, maxDepth)
    suspend fun deleteRecursively(): Boolean = fileSystem deleteRecursively path
    suspend infix fun copyFrom(from: DirectoryEntry) = fileSystem.copyFrom(path, from)
    suspend infix fun moveFrom(from: DirectoryEntry) = fileSystem.moveFrom(path, from)

}