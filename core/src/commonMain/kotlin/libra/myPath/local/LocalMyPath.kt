package libra.myPath.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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
import okio.SYSTEM


@Serializable
@SerialName("LocalMyPath")
sealed class LocalMyPath : MyPath {
    @Transient
    val path: Path = rawPath.stripPrefix().toPath()

    final override val name: String get() = path.name

    @Transient
    final override var metadata: FileMetadata? = null
        protected set

    final override fun toString(): String = rawPath

    final override suspend fun statOrNull(): MyPath? {
        metadata = withContext(Dispatchers.IO) { FileSystem.SYSTEM.metadataOrNull(path) }
        metadata ?: return null
        return asMyFile() ?: asMyDirectory()
    }


    final override suspend fun mk(dir: Boolean): MyPath = apply {
        when (dir) {
            true -> withContext(Dispatchers.IO) {
                FileSystem.SYSTEM.createDirectories(path, false)
            }

            false -> asMyFile(false)?.write(byteArrayOf(), true)
        }
    }
}