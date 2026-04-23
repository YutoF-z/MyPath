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
open class LocalPath private constructor(
    override val rawPath: String,
    @Transient val path: Path = Path(rawPath.stripPrefix())
): MyPath {
    @Transient
    override val name: String = path.name
    @Transient
    final override var metadata: FileMetadata? = null
        private set

    constructor(
        rawPath: String,
        _metadata: FileMetadata? = null
    ): this(rawPath) {
        metadata = _metadata
    }

    override suspend fun stat(): LocalPath = statOrNull() ?: this
    override suspend fun statOrNull(): LocalPath? {
        withContext(Dispatchers.IO) {
            metadata = SystemFileSystem.metadataOrNull(path)
            metadata ?: return@withContext null
        }
        return asMyFile() ?: asMyDirectory()
    }

    override suspend fun metadataOrNull(): FileMetadata? =
        metadata ?: stat().metadata

    open override suspend fun toMyDirectory(): MyDirectory? = 
        when(metadataOrNull()?.isDirectory ?: true) {
            true -> LocalDirectory(this)
            false -> null
        }
    open override suspend fun toMyFile(): MyFile? = 
        when(metadataOrNull()?.isRegularFile ?: true) {
            true -> LocalFile(this)
            false -> null
        }
    
    open override suspend fun asMyDirectory(): LocalDirectory? = 
        this as? LocalDirectory 
        ?: when(metadataOrNull()?.isDirectory ?: false) {
            true -> LocalDirectory(this)
            false -> null
        }
    open override suspend fun asMyFile(): LocalFile? = 
        this as? LocalFile 
        ?: when(metadataOrNull()?.isRegularFile ?: false) {
            true -> LocalFile(this)
            false -> null
        }

    override suspend fun mv(destination: MyPath): MyPath {
        statOrNull() ?: throw FileNotFoundException(rawPath)
        
        when {
            metadataOrNull()?.isDirectory && destination.metadataOrNull()?.isRegularFile ->
                error("Filetype Not match")
            metadataOrNull()?.isRegularFile && destination.metadataOrNull()?.isDirectory ->
                error("Filetype Not match")
            destination is LocalPath -> withContext(Dispatchers.IO) { 
                SystemFileSystem.atomicMove(path, destination.path)
            }         
        }
    }
    override suspend fun cp(destination: MyPath): MyPath
    override suspend fun mk(): MyPath
    override suspend fun rm()
}
