package libra.myPath.uriPath

import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import libra.myPath.MyDirectory
import libra.myPath.MyPath


fun String.toUriDirectory() = UriDirectory(this)

@Serializable
@SerialName("UriFile")
class UriDirectory(
    override val rawPath: String
) : UriPath(), MyDirectory {
    override fun documentFile(): DocumentFile? = DocumentFile.fromTreeUri(context, path)

    override fun list(
        contains: String?,
        filter: (MyPath.() -> Boolean)?
    ): Flow<UriPath> = flow {
        documentFile()?.listFiles()?.forEach {
            if (contains != null && it.name?.contains(contains) ?: true)
                return@forEach

            contains?.let { it1 ->
                if (it1 !in it.toString()) return@forEach
            }

            val path = when (it.isDirectory) {
                true -> it.toString().toUriDirectory()
                false -> it.toString().toUriFile()
            }

            filter?.let { it1 ->
                if (!path.it1()) return@forEach
            }

            emit(path)
        }
    }

    override suspend fun fileWith(name: String): UriFile? = withContext(Dispatchers.IO) {
        documentFile()?.findFile(name)?.name?.toUriFile()
            ?: mkFile(name)
    }

    override suspend fun dirWith(name: String): UriDirectory? = withContext(Dispatchers.IO) {
        documentFile()?.findFile(name)?.name?.toUriDirectory()
            ?: mkDir(name)
    }

    override suspend fun mkDir(name: String): UriDirectory? =
        documentFile()?.createDirectory(name)?.uri?.path?.let { UriDirectory(it) }

    override suspend fun mkFile(name: String): UriFile? =
        documentFile()?.createFile(
            "application/${name.substringAfterLast('.', "octet-stream")}",
            name.substringBeforeLast('.', name)
        )?.uri?.path?.let { UriFile(it) }
}