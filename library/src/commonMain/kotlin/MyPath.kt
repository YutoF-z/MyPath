package libra.myPath

import kotlinx.io.files.FileMetadata

interface MyPath {
    suspend fun rawPath(): String
    suspend fun name(): String?
    suspend fun extension(): String?
    suspend fun isWritable(): Boolean
    suspend fun fileMetadata(): FileMetadata?

    suspend fun stat(): FileMetadata?
}
