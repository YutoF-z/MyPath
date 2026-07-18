package libra.myPath.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import libra.myPath.MyDirectory
import libra.myPath.MyFile
import libra.myPath.MyPath
import libra.myPath.MyPath.Companion.stripPrefix
import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath


@Serializable
@SerialName("LocalPath")
sealed class LocalPath : MyPath {
    val path: Path get() = rawPath.stripPrefix().toPath()

    final override suspend fun name(): String = path.name

    final override suspend fun metadata(): FileMetadata? =
        withContext(Dispatchers.IO) { FileSystem.SYSTEM.metadataOrNull(path) }

    final override suspend fun exists(): Boolean = withContext(Dispatchers.IO) {
        FileSystem.SYSTEM.exists(path)
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        val LocalModule by lazy {
            SerializersModule {
                polymorphic(MyPath::class) {
                    subclassesOfSealed<LocalPath>()
                }
                polymorphic(MyDirectory::class) {
                    subclass(LocalDirectory::class)
                }
                polymorphic(MyFile::class) {
                    subclass(LocalFile::class)
                }
            }
        }
    }

}