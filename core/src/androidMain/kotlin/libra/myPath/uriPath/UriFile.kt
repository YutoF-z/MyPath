package libra.myPath.uriPath

import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import libra.myPath.MyFile
import okio.Sink
import okio.Source
import okio.sink
import okio.source


@Serializable
@SerialName("UriFile")
class UriFile(
    override val rawPath: String
) : UriPath(), MyFile {
    override suspend fun source(): Source =
        context.contentResolver.openInputStream(path)!!.source()

    override suspend fun sink(append: Boolean): Sink =
        context.contentResolver.openOutputStream(path, if (append) "wa" else "w")!!.sink()

    override fun documentFile(): DocumentFile? = DocumentFile.fromSingleUri(context, path)


    override suspend infix fun copyFrom(destination: MyFile) =
        if (destination is UriFile) copyFrom(destination)
        else super copyFrom destination

    suspend infix fun copyFrom(destination: UriFile) = withContext(Dispatchers.IO) {
        val resultUri = DocumentsContract.copyDocument(
            context.contentResolver, path, destination.path
        ) ?: throw Exception("Copy failed")
    }

    override suspend infix fun moveFrom(destination: MyFile) =
        if (destination is UriFile) moveFrom(destination)
        else super moveFrom destination

    suspend infix fun moveFrom(destination: UriFile) = withContext(Dispatchers.IO) {
        val destUri = destination.path
        val parentUri = getParentUri(path)
        val destParentUri = getParentUri(destUri)

        // API 24+ の移動処理
        val resultUri = DocumentsContract.moveDocument(
            context.contentResolver, path, parentUri, destParentUri
        ) ?: throw Exception("Move failed")
    }
}