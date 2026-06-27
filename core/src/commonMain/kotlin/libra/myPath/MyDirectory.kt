package libra.myPath

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Polymorphic


@Polymorphic
interface MyDirectory : MyPath {
    suspend fun list(pattern: Regex? = null): Sequence<MyPath>
    suspend fun listRecursively(pattern: Regex? = null): Sequence<MyPath>

    infix fun fileWith(name: String): MyFile
    infix fun dirWith(name: String): MyDirectory

    suspend fun mk(): MyDirectory?
    suspend fun rm()
    suspend infix fun copyFrom(destination: MyDirectory): MyDirectory = apply { copy(destination) }
    suspend infix fun moveFrom(destination: MyDirectory): MyDirectory = apply {
        withContext(Dispatchers.IO) {
            runCatching {
                copyFrom(destination)
                statOrNull()
            }.getOrNull()?.run { destination.rm() }
        }
    }

    private suspend fun copy(destination: MyDirectory, base: MyDirectory = this) {
        withContext(Dispatchers.IO) {
            destination.list().forEach {
                it.onEach(
                    { (base fileWith name.toString()).copyFrom(this) },
                    { copy(this, base dirWith it.name.toString()) },
                    {}
                )
            }
        }
    }
}