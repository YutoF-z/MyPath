package libra.myPath

import kotlinx.io.files.FileMetadata
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

@Serializable
@SerialName("LocalPath")
open class LocalPath(
    override val rawPath: String
    @Transient val path: Path = Path(rawPath.stripPrefix())
    _metadata: FileMetadata? = null
): MyPath {
    override suspend fun statOrNull(): LocalPath? {
        withContext(Dispatchers.IO) { 
            _metadata = SystemFileSystem.metadataOrNull(path)
            _metadata ?: return null
        }
        return asMyFile() ?: asMyDirectory()
    }
    override suspend fun stat(): LocalPath = statOrNull() ?: this

    override val name: String = path.name
    override var metadata: FileMetadata? = _metadata
        private set
    
    open override suspend fun asMyDirectory(): LocalDirectory? = 
        this as? LocalDirectory 
        ?: if(metadata()?.isDirectory ?: false) LocalDirectory(this) else null
    open override suspend fun asMyFile(): LocalFile? = 
        this as? LocalFile 
        ?: if(metadata()?.isRegularFile ?: false) LocalFile(this) else null

    override suspend fun mv(destination: MyPath): MyPath = apply { withContext(Dispatchers.IO) {
        when (destination) {
            is LocalPath -> SystemFileSystem.atomicMove(path, destination.path)
            
        }
    }}
    override suspend fun cp(destination: MyPath): MyPath
    override suspend fun mk(): MyPath
    override suspend fun rm()
}
