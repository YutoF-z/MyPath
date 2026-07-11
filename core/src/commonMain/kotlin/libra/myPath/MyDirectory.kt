package libra.myPath

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.withContext
import kotlinx.serialization.Polymorphic


@Polymorphic
interface MyDirectory : MyPath {
    fun list(contains: String? = null, filter: (MyPath.() -> Boolean)? = null): Flow<MyPath>
    fun listRecursively(
        contains: String? = null,
        filter: (MyPath.() -> Boolean)? = null
    ): Flow<MyPath>

    infix fun fileWith(name: String): MyFile
    infix fun dirWith(name: String): MyDirectory

    suspend fun mk(): MyDirectory?
    suspend fun rm()
    suspend infix fun copyFrom(destination: MyDirectory): MyDirectory = apply {
        withContext(Dispatchers.IO) {
            copy(destination)
        }
    }

    suspend infix fun moveFrom(destination: MyDirectory): MyDirectory = apply {
        withContext(Dispatchers.IO) {
            runCatching {
                copyFrom(destination)
                statOrNull()
            }.getOrNull()?.run { destination.rm() }
        }
    }

    private suspend fun copy(destination: MyDirectory, base: MyDirectory = this) {
        destination.list()
            .buffer(capacity = 64)
            .collect {
                it.onEach(
                    { base.fileWith(name.toString()).copyFrom(this) },
                    {
                        base.dirWith(it.name.toString()).let { dir ->
                            dir.mk()
                            copy(this, dir)
                        }
                    }
                )
            }
    }
}