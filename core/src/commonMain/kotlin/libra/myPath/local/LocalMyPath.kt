package libra.myPath.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileMetadata
import okio.Path
import okio.SystemFileSystem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import libra.myPath.MyPath
import libra.myPath.stripPrefix


@Serializable
@SerialName("LocalMyPath")
sealed class LocalMyPath : MyPath {
    @Transient
    val path: Path = Path(rawPath.stripPrefix())

    final override val name: String get() = path.name

    @Transient
    final override var metadata: FileMetadata? = null
        protected set

    final override fun toString(): String = rawPath

    final override suspend fun statOrNull(): MyPath? {
        metadata = withContext(Dispatchers.IO) { SystemFileSystem.metadataOrNull(path) }
        metadata ?: return null
        return asMyFile() ?: asMyDirectory()
    }


    final override suspend fun mk(dir: Boolean): MyPath = apply {
        when (dir) {
            true -> withContext(Dispatchers.IO) {
                SystemFileSystem.createDirectories(path, false)
            }

            false -> asMyFile(false)?.writeByteArray(byteArrayOf(), true)
        }
    }
}