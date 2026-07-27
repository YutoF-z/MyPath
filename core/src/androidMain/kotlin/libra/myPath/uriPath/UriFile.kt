package libra.myPath.uriPath

import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import libra.myPath.MyFile
import okio.Sink
import okio.Source
import okio.sink
import okio.source


fun String.toUriFile() = UriFile(this)

@JvmInline
@Serializable
@SerialName("UriFile")
value class UriFile(
    override val rawPath: String
) : UriPath, MyFile {
    override suspend fun source(): Source =
        UriPath.context.contentResolver.openInputStream(path)!!.source()

    override suspend fun sink(append: Boolean): Sink =
        UriPath.context.contentResolver.openOutputStream(path, if (append) "wa" else "w")!!.sink()

    override fun documentFile(): DocumentFile? = DocumentFile.fromSingleUri(UriPath.context, path)
}