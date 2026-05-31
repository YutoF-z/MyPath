package libra.myPath.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileNotFoundException
import okio.FileSystem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import libra.myPath.MyPathInterface
import okio.SYSTEM

@Serializable
@SerialName("LocalPath")
class LocalPath(
    override val rawPath: String
) : LocalMyPath() {
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

    override suspend fun mvFrom(destination: MyPathInterface): MyPathInterface {
        metadataOrNull() ?: throw FileNotFoundException(rawPath)

        when {
            metadata!!.isDirectory && destination.metadataOrNull()?.isRegularFile == true ->
                error("Filetype Not match")

            metadata!!.isRegularFile && destination.metadataOrNull()?.isDirectory == true ->
                error("Filetype Not match")

            destination is LocalMyPath -> withContext(Dispatchers.IO) {
                FileSystem.SYSTEM.atomicMove(path, destination.path)
            }

            metadata!!.isRegularFile -> asMyFile()?.moveFrom(destination)
            else -> asMyDirectory()?.moveFrom(destination)
        }
        return destination
    }

    override suspend fun cpFrom(destination: MyPathInterface): MyPathInterface {
        metadataOrNull() ?: throw FileNotFoundException(rawPath)

        when {
            metadata!!.isDirectory && destination.metadataOrNull()?.isRegularFile == true ->
                error("Filetype Not match")

            metadata!!.isRegularFile && destination.metadataOrNull()?.isDirectory == true ->
                error("Filetype Not match")

            metadata!!.isRegularFile -> asMyFile()?.copyFrom(destination)
            else -> asMyDirectory()?.copyFrom(destination)
        }
        return destination
    }

    override suspend fun rm() {
        metadataOrNull() ?: throw FileNotFoundException(rawPath)
        when (metadata!!.isRegularFile) {
            true -> asMyFile()?.rm()
            false -> asMyDirectory()?.rm()
        }
    }
}
