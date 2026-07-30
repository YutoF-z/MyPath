package libra.myPath.samba

import libra.myPath.MyPath


expect sealed class SambaPath : MyPath {
    override val rawPath: String

    override suspend fun name(): String?
    override suspend fun exists(): Boolean
    override suspend fun metadata(): okio.FileMetadata
}