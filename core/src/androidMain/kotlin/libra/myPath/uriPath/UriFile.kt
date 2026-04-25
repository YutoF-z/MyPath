package libra.myPath.uriPath

import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import libra.myPath.MyFile
import libra.myPath.MyPath

@Serializable
@SerialName("UriFile")
class UriFile(
    override val rawPath: String
) : UriMyPath(), MyFile {
    override suspend fun asMyDirectory(mustExist: Boolean): UriDirectory? = null
    override suspend fun asMyFile(mustExist: Boolean): UriFile = this

    override val documentFile: DocumentFile? by lazy {
        DocumentFile.fromSingleUri(context, uri)
    }

    override suspend fun source(): RawSource {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Failed to open input stream")
        return inputStream.asSource()
    }

    override suspend fun sink(append: Boolean): RawSink {
        val mode = if (append) "wa" else "w"
        val outputStream = context.contentResolver.openOutputStream(uri, mode)
            ?: throw Exception("Failed to open output stream")
        return outputStream.asSink()
    }


    override suspend fun mk(dir: Boolean): MyPath {
        // parentUri と displayName を分離して DocumentsContract.createDocument を呼ぶ実装が必要
        // 簡易的には DocumentFile.createFile / createDirectory を使用
        val parentUri = getParentUri(uri) // 補助関数で親のURIを取得
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