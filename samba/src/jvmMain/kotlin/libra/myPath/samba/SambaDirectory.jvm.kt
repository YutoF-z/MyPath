package libra.myPath.samba

import kotlinx.coroutines.flow.Flow
import libra.myPath.MyDirectory
import libra.myPath.MyFile
import libra.myPath.MyPath

actual class SambaDirectory actual constructor(rawPath: String) : MyDirectory,
    SambaPath() {
    override fun list(
        contains: String?,
        filter: (MyPath.() -> Boolean)?
    ): Flow<MyPath> {
        TODO("Not yet implemented")
    }

    override suspend fun fileWith(name: String): MyFile? {
        TODO("Not yet implemented")
    }

    override suspend fun dirWith(name: String): MyDirectory? {
        TODO("Not yet implemented")
    }

    override suspend fun mkDir(name: String): MyDirectory? {
        TODO("Not yet implemented")
    }

    override suspend fun rm() {
        TODO("Not yet implemented")
    }
}