package libra.myPath.local

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
    override val rawPath: String
): MyPath {
    @Transient 
    val path: Path = Path(rawPath.stripPrefix())
    
    override val name: String get() = path.name
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

    open override suspend fun asMyDirectory(
        mustExist: Boolean = true
    ): LocalDirectory? = 
        this as? LocalDirectory 
        ?: when(metadataOrNull()?.isDirectory ?: !mustExist) {
            true -> LocalDirectory(this)
            false -> null
        }
    open override suspend fun asMyFile(
        mustExist: Boolean = true
    ): LocalFile? = 
        this as? LocalFile 
        ?: when(metadataOrNull()?.isRegularFile ?: !mustExist) {
            true -> LocalFile(this)
            false -> null
        }

    open override suspend fun mv(destination: MyPath): MyPath {
        statOrNull() ?: throw FileNotFoundException(rawPath)
        
        when {
            metadata?.isDirectory && destination.metadataOrNull()?.isRegularFile ->
                error("Filetype Not match")
            metadata?.isRegularFile && destination.metadataOrNull()?.isDirectory ->
                error("Filetype Not match")
                
            destination is LocalPath -> withContext(Dispatchers.IO) { 
                SystemFileSystem.atomicMove(path, destination.path)
            }
            metadata?.isRegularFile -> asMyFile()?.mv(destination)
            else -> asMyDirectory()?.mv(destination)
        }
        return destination
    }
    
    open override suspend fun cp(destination: MyPath): MyPath {
        statOrNull() ?: throw FileNotFoundException(rawPath)
        
        when {
            metadata?.isDirectory && destination.metadataOrNull()?.isRegularFile ->
                error("Filetype Not match")
            metadata?.isRegularFile && destination.metadataOrNull()?.isDirectory ->
                error("Filetype Not match")
            metadata?.isRegularFile -> asMyFile()?.cp(destination)
            else -> asMyDirectory()?.cp(destination)
        }
        return destination
    }
    
    override suspend fun mk(dir:Boolean = true): MyPath = apply {
        when(dir) {
            true -> withContext(Dispatchers.IO) { 
                SystemFileSystem.createDirectories(path, false)
            }
            false -> asMyFile(false)?.writeByteArray(byteArrayOf(), true)
        }
    }
    
    open override suspend fun rm() {
        statOrNull() ?: throw FileNotFoundException(rawPath)
        when(metadata?.isRegularFile) {
            true -> asMyFile()?.rm()
            false -> asMyDirectory()?.rm()
        }
    }
}
