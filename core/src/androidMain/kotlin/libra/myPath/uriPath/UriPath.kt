package libra.myPath.uriPath

import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import libra.myPath.MyPath

@Serializable
@SerialName("UriPath")
class UriPath(
    override val rawPath: String
) : UriMyPath() {
    override suspend fun asMyDirectory(mustExist: Boolean): UriDirectory? = null
    override suspend fun asMyFile(mustExist: Boolean): UriFile? = this

    override suspend fun mk(dir: Boolean): MyPath {
        val parentUri = getParentUri(uri)
        val parentDoc = DocumentFile.fromTreeUri(context, parentUri)
        val mimeType =
            if (dir) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream"
        val newFile = parentDoc?.createFile(mimeType, name ?: "new_item")
            ?: throw Exception("Failed to create")
        return UriMyPath(context, newFile.uri)
    }

    override suspend fun mv(destination: MyPath): MyPath {
        val destUri = Uri.parse(destination.rawPath)
        val parentUri = getParentUri(uri)
        val destParentUri = getParentUri(destUri)

        // API 24+ の移動処理
        val resultUri = DocumentsContract.moveDocument(
            context.contentResolver, uri, parentUri, destParentUri
        ) ?: throw Exception("Move failed")

        return UriMyPath(context, resultUri)
    }

    override suspend fun cp(destination: MyPath): MyPath {
        // API 24+ DocumentsContract.copyDocument
        val resultUri = DocumentsContract.copyDocument(
            context.contentResolver, uri, Uri.parse(destination.rawPath)
        ) ?: throw Exception("Copy failed")

        return UriMyPath(context, resultUri)
    }
}