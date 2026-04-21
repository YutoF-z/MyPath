package libra.myPath

import kotlinx.serialization.Polymorphic


@Polymorphic
interface MyDirectory: MyPath {
    override suspend fun toMyDirectory(): MyDirectory? = this
    override suspend fun toMyFile(): MyFile? = null

    fun list(pattern: Regex? = null): Sequence<MyPath>
    suspend fun mkDirs(): MyDirectory
}