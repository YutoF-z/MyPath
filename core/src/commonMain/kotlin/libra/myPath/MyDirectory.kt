package libra.myPath

import kotlinx.serialization.Polymorphic


@Polymorphic
interface MyDirectory: MyPath {
    override suspend fun asMyDirectory(mustExist: Boolean): MyDirectory? = this
    override suspend fun asMyFile(mustExist: Boolean): MyFile? = null

    fun list(pattern: Regex? = null): Sequence<MyPath>
}