package libra.myPath

import kotlinx.serialization.Polymorphic


@Polymorphic
interface MyDirectory : MyPath {
    override suspend fun asMyDirectory(mustExist: Boolean): MyDirectory? = this
    override suspend fun asMyFile(mustExist: Boolean): MyFile? = null

    fun list(pattern: Regex? = null): Sequence<MyPath>
    fun listRecursively(pattern: Regex? = null): Sequence<MyPath>

    suspend fun mv(destination: MyDirectory): MyDirectory
    override suspend fun mv(destination: MyPath): MyPath? = when {
        destination is MyDirectory -> mv(destination)
        destination.metadataOrNull()?.isDirectory == true -> asMyDirectory()!!.mv(destination)
        else -> null
    }

    suspend fun cp(destination: MyDirectory): MyDirectory
    override suspend fun cp(destination: MyPath): MyPath? = when {
        destination is MyDirectory -> cp(destination)
        destination.metadataOrNull()?.isDirectory == true -> asMyDirectory()!!.cp(destination)
        else -> null
    }
}