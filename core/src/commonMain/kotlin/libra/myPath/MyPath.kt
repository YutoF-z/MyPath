package libra.myPath

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Transient
import okio.FileMetadata


@Polymorphic
interface MyPath {
    val rawPath: String

    @Transient
    val name: String?

    @Transient
    val metadata: FileMetadata?



    suspend fun stat(): MyPath = statOrNull() ?: this
    suspend fun metadataOrNull(): FileMetadata? = metadata ?: stat().metadata

    suspend fun statOrNull(): MyPath?
    
    suspend fun asMyDirectory(mustExist: Boolean = true): MyDirectory?
    suspend fun asMyFile(mustExist: Boolean = true): MyFile?

    suspend fun mv(destination: MyPath): MyPath
    suspend fun cp(destination: MyPath): MyPath
    suspend fun mk(dir:Boolean = true): MyPath
    suspend fun rm()
}
