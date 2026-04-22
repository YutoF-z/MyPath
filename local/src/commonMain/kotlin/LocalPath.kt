package libra.myPath

import kotlinx.io.files.FileMetadata
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

@Serializable
@SerialName("LocalPath")
open class LocalPath(
    override val rawPath: String
    @Transient val path: Path = Path(rawPath.stripPrefix())
    @Transient private var _metadata: FileMetadata? = null
): MyPath {
    override suspend fun stat(): LocalPath? = withContext(Dispatchers.IO) { 
        SystemFileSystem.metadataOrNull(path).also { metadata_ = it } 
    }

    override suspend fun name(): String = path.name
    override suspend fun metadata(): FileMetadata? = _metadata ?: stat().metadata()
    open override suspend fun asMyDirectory(): LocalDirectory? = this as? LocalDirectory ?: if(metadata()?.isDirectory ?: false) LocalDirectory(this) else null
    open override suspend fun asMyFile(): LocalFile? = this as? LocalFile ?: if(metadata()?.isRegularFile ?: false) LocalFile(this) else null

    override suspend fun mv(destination: MyPath): MyPath = apply { withContext(Dispatchers.IO) {
        when (destination) {
            is LocalPath -> SystemFileSystem.atomicMove(path, destination.path)
            
        }
    }}
    override suspend fun cp(destination: MyPath): MyPath
    override suspend fun mk(): MyPath
    override suspend fun rm()
}
