package libra.myPath

import kotlinx.io.files.FileMetadata
import kotlinx.io.files.Path

class LocalDirectory(
    override val rawPath: String,
    _path: Path = Path(rawPath.stripPrefix()),
    _metadata: FileMetadata? = null
) : LocalPath(rawPath, _path, _metadata), MyDirectory {
    override suspend fun toMyDirectory(): MyDirectory? {
        TODO("Not yet implemented")
    }

    override suspend fun toMyFile(): MyFile? {
        TODO("Not yet implemented")
    }

    override suspend fun toMyDirectory(): MyDirectory? {
        TODO("Not yet implemented")
    }

    override suspend fun toMyFile(): MyFile? {
        TODO("Not yet implemented")
    }

    override fun list(pattern: Regex?): Sequence<MyPath> {
        TODO("Not yet implemented")
    }

    override suspend fun mkDirs(): MyDirectory {
        TODO("Not yet implemented")
    }
}