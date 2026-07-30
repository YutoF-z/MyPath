package libra.myPath.samba

import libra.myPath.MyPath
import okio.FileMetadata

actual sealed class SambaPath : MyPath {
    actual override val rawPath: String
        get() = TODO("Not yet implemented")

    actual override suspend fun name(): String? {
        TODO("Not yet implemented")
    }

    actual override suspend fun exists(): Boolean {
        TODO("Not yet implemented")
    }

    actual override suspend fun metadata(): FileMetadata {
        TODO("Not yet implemented")
    }
}