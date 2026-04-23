package libra.myPath

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.files.FileMetadata
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.FileNotFoundException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@SerialName("LocalPath")
open class LocalPath(
    override val rawPath: String,
    @Transient val path: Path = Path(rawPath.stripPrefix()),
    _metadata: FileMetadata? = null
): MyPath {
    override suspend fun stat(): LocalPath = statOrNull() ?: this
    override suspend fun statOrNull(): LocalPath? {
        withContext(Dispatchers.IO) {
            metadata = SystemFileSystem.metadataOrNull(path)
            metadata ?: return@withContext null
        }
        return asMyFile() ?: asMyDirectory()
    }

    @Transient
    override val name: String = path.name


    @Transient
    final override var metadata: FileMetadata? = _metadata
        private set

    open override suspend fun toMyDirectory(): MyDirectory? = 
        when(metadata()?.isDirectory ?: true) {
            true -> LocalDirectory(this)
            false -> null
        }
    open override suspend fun toMyFile(): MyFile? = 
        when(metadata()?.isRegularFile ?: true) {
            true -> LocalFile(this)
            false -> null
        }
    
    open override suspend fun asMyDirectory(): LocalDirectory? = 
        this as? LocalDirectory 
        ?: when(metadata()?.isDirectory ?: false) {
            true -> LocalDirectory(this)
            false -> null
        }
    open override suspend fun asMyFile(): LocalFile? = 
        this as? LocalFile 
        ?: when(metadata()?.isRegularFile ?: false) {
            true -> LocalFile(this)
            false -> null
        }

    override suspend fun mv(destination: MyPath): MyPath {
        statOrNull() ?: throw FileNotFoundException(rawPath)
        
        when {
            metadata()?.isDirectory && 
            destination is LocalPath -> withContext(Dispatchers.IO) { 
                SystemFileSystem.atomicMove(path, destination.path)
            }         
        }
    }
    override suspend fun cp(destination: MyPath): MyPath
    override suspend fun mk(): MyPath
    override suspend fun rm()
}
