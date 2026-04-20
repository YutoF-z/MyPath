package libra.myPath

import kotlinx.io.files.FileMetadata
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem


open class LocalPath(override val rawPath: String): MyPath {
    val path: Path
    private val name: String = path.name
    private var extension: String? = null
    private var isWritable: Boolean = false
    private var fileMetadata: FileMetadata? = null
    
    
    override fun stat(): FileMetadata? {
        stated = true
        return SystemFileSystem.metadataOrNull(path).also { 
            fileMetadata = it 
        }
    }
}
