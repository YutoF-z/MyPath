package libra.myPath

import kotlinx.io.files.FileMetadata
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Transient


@Polymorphic
interface MyPath {
    val rawPath: String

    @Transient
    val name: String?

    @Transient
    val metadata: FileMetadata?

    suspend fun stat(): MyPath
    suspend fun statOrNull(): MyPath?
    suspend fun metadataOrNull(): FileMetadata?
    
    suspend fun toMyDirectory(): MyDirectory?
    suspend fun toMyFile(): MyFile?
    
    suspend fun asMyDirectory(): MyDirectory?
    suspend fun asMyFile(): MyFile?

    suspend fun mv(destination: MyPath): MyPath
    suspend fun cp(destination: MyPath): MyPath
    suspend fun mk(): MyPath
    suspend fun rm()
}
