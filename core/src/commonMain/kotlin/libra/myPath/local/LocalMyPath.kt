package libra.myPath.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import libra.myPath.MyPathInterface
import libra.myPath.stripPrefix
import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM


@Serializable
@SerialName("LocalMyPath")
sealed class LocalMyPath : MyPathInterface {
    @Transient
    val path: Path = rawPath.stripPrefix().toPath()

    final override val name: String get() = path.name

    @Transient
    var metadata: FileMetadata? = null
        protected set

    override suspend fun metadataOrNull(): FileMetadata? = metadata ?: statOrNull()?.metadata

    final override fun toString(): String = rawPath

    final override suspend fun statOrNull(): LocalMyPath? {
        metadata = withContext(Dispatchers.IO) { FileSystem.SYSTEM.metadataOrNull(path) }
        metadata ?: return null
        return this
    }
}