package libra.myPath

import kotlinx.io.files.FileMetadata

interface MyPath {
    val rawPath: String
    val name: String?
    val extension: String?
    val isWritable: Boolean
    val fileMetadata: FileMetadata?

    suspend fun stat(): FileMetadata?
}