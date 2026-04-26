package libra.myPath.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileNotFoundException
import okio.FileSystem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import libra.myPath.MyPath
import okio.SYSTEM

@Serializable
@SerialName("LocalPath")
sealed class LocalPath(
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

    override suspend fun mv(destination: MyPath): MyPath {
        metadataOrNull() ?: throw FileNotFoundException(rawPath)

        when {
            metadata!!.isDirectory && destination.metadataOrNull()?.isRegularFile == true ->
                error("Filetype Not match")

            metadata!!.isRegularFile && destination.metadataOrNull()?.isDirectory == true ->
                error("Filetype Not match")

            destination is LocalMyPath -> withContext(Dispatchers.IO) {
                FileSystem.SYSTEM.atomicMove(path, destination.path)
            }

            metadata!!.isRegularFile -> asMyFile()?.mv(destination)
            else -> asMyDirectory()?.mv(destination)
        }
        return destination
    }

    override suspend fun cp(destination: MyPath): MyPath {
        metadataOrNull() ?: throw FileNotFoundException(rawPath)

        when {
            metadata!!.isDirectory && destination.metadataOrNull()?.isRegularFile == true ->
                error("Filetype Not match")

            metadata!!.isRegularFile && destination.metadataOrNull()?.isDirectory == true ->
                error("Filetype Not match")

            metadata!!.isRegularFile -> asMyFile()?.cp(destination)
            else -> asMyDirectory()?.cp(destination)
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
