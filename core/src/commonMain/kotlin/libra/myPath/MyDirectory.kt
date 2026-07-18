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


    suspend fun mkFile(name: String): MyFile? =
        fileWith(name).apply { write(byteArrayOf(), append = true) }

    suspend fun mkDir(name: String): MyDirectory?

    suspend infix fun copyFrom(destination: MyDirectory) {
        withContext(Dispatchers.IO) {
            copy(destination, this@MyDirectory)
        }
    }

    suspend infix fun moveFrom(destination: MyDirectory) {
        withContext(Dispatchers.IO) {
            runCatching {
                copyFrom(destination)
                exists()
            }.getOrNull()?.run { destination.rm() }
        }
    }

}

private suspend fun copy(destination: MyDirectory, base: MyDirectory) {
    destination.list()
        .buffer(capacity = 64)
        .collect {
            it.onEach(
                { base.fileWith(name().toString()).copyFrom(this) },
                {
                    copy(this, base.mkDir(it.name().toString())!!)
                }
            )
        }
}