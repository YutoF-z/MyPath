package libra.myPath

import kotlinx.serialization.Serializable

@Serializable
sealed interface Entry {
    val path: Path
    val fileSystem: FileSystem


    suspend fun name() = fileSystem name path
    suspend fun exists() = fileSystem exists path
    suspend fun metadata() = fileSystem metadata path
    suspend fun isTreePath() = fileSystem isTreePath path
}
