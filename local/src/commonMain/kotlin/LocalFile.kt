package libra.myPath

import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.files.FileMetadata
import kotlinx.io.files.Path


class LocalFile(
    override val rawPath: String,
    _path: Path = Path(rawPath.stripPrefix()),
    _metadata: FileMetadata? = null
) : LocalPath(rawPath, _path, _metadata), MyFile {
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

    override suspend fun source(): RawSource {
        TODO("Not yet implemented")
    }

    override suspend fun sink(append: Boolean): RawSink {
        TODO("Not yet implemented")
    }
}