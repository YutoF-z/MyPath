package libra.myPath.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import libra.myPath.MyPath
import libra.myPath.stripPrefix
import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath


@Serializable
@SerialName("LocalPath")
sealed class LocalPath : MyPath {
    @Transient
    val path: Path = rawPath.stripPrefix().toPath()

    final override val name: String get() = path.name

    @Transient
    var metadata: FileMetadata? = null
        protected set

    final override suspend fun metadataOrNull(): FileMetadata? =
        metadata ?: statOrNull()?.metadata

    final override suspend fun statOrNull(): LocalPath? {
        metadata = withContext(Dispatchers.IO) { FileSystem.SYSTEM.metadataOrNull(path) }
        metadata ?: return null
        return this
    }
}