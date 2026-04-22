package libra.myPath

import kotlinx.io.files.FileMetadata
import kotlinx.serialization.Polymorphic


@Polymorphic
interface MyPath {
    val rawPath: String
    val name: String?
    val metadata: FileMetadata?

    suspend fun stat(): MyPath
    suspend fun statOrNull(): MyPath?
    
    suspend fun toMyDirectory(): MyDirectory?
    suspend fun toMyFile(): MyFile?
    
    suspend fun asMyDirectory(): MyDirectory?
    suspend fun asMyFile(): MyFile?

    suspend fun mv(destination: MyPath): MyPath
    suspend fun cp(destination: MyPath): MyPath
    suspend fun mk(): MyPath
    suspend fun rm()
}
