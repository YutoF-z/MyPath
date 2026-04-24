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
    
    suspend fun asMyDirectory(mustExist: Boolean = true): MyDirectory?
    suspend fun asMyFile(mustExist: Boolean = true): MyFile?

    suspend fun mv(destination: MyPath): MyPath
    suspend fun cp(destination: MyPath): MyPath
    suspend fun mk(): MyPath
    suspend fun rm()
}
