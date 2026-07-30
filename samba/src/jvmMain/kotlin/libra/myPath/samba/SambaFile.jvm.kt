package libra.myPath.samba

import libra.myPath.MyFile
import okio.Sink
import okio.Source

actual class SambaFile actual constructor(rawPath: String) : MyFile,
    SambaPath() {
    override suspend fun source(): Source {
        TODO("Not yet implemented")
    }

    override suspend fun sink(append: Boolean): Sink {
        TODO("Not yet implemented")
    }

    override suspend fun rm() {
        TODO("Not yet implemented")
    }
}