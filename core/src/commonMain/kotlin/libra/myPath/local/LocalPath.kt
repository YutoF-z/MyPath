package libra.myPath.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import libra.myPath.MyPath

@Serializable
@SerialName("LocalPath")
class LocalPath(
    override val rawPath: String
) : LocalMyPath(), MyPath {
    override suspend fun asMyDirectory(
        mustExist: Boolean
    ): LocalDirectory? = when (metadataOrNull()?.isDirectory ?: !mustExist) {
        true -> LocalDirectory(this)
        false -> null
    }

    override suspend fun asMyFile(
        mustExist: Boolean
    ): LocalFile? = when (metadataOrNull()?.isRegularFile ?: !mustExist) {
        true -> LocalFile(this)
        false -> null
    }
}
