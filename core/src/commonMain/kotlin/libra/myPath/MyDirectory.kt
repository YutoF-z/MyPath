package libra.myPath

import kotlinx.serialization.Polymorphic


@Polymorphic
interface MyDirectory : MyPathInterface {
    fun list(pattern: Regex? = null): Sequence<MyPathInterface>
    fun listRecursively(pattern: Regex? = null): Sequence<MyPathInterface>


    suspend fun copyFrom(destination: MyDirectory): MyDirectory
    suspend fun moveFrom(destination: MyDirectory): MyDirectory = apply {
        copyFrom(destination)
        runCatching { statOrNull() }.getOrNull()?.run {
            destination.rm()
        }
    }

    suspend fun mk(): MyDirectory?
}

suspend fun MyDirectory.moveTo(destination: MyDirectory): MyDirectory = destination.moveFrom(this)
suspend fun MyDirectory.copyTo(destination: MyDirectory): MyDirectory = destination.copyFrom(this)