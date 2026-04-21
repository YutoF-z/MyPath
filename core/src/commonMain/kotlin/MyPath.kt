package libra.myPath

import kotlinx.io.files.FileMetadata
import kotlinx.serialization.Polymorphic


@Polymorphic
interface MyPath {
    val rawPath: String

    suspend fun stat(): MyPath?

    suspend fun name(): String?
    suspend fun metadata(): FileMetadata?
    suspend fun toMyDirectory(): MyDirectory?
    suspend fun toMyFile(): MyFile?

    suspend fun mv(destination: MyPath): MyPath
    suspend fun cp(destination: MyPath): MyPath
    suspend fun mk(): MyPath
    suspend fun rm()
}
