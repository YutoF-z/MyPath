package libra.myPath.uriPath

import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import libra.myPath.MyDirectory
import libra.myPath.MyPath


@Serializable
@SerialName("UriFile")
class UriDirectory(
    override val rawPath: String
) : UriPath(), MyDirectory {
    override fun documentFile(): DocumentFile? = DocumentFile.fromTreeUri(context, path)

    override fun list(
        contains: String?,
        filter: (MyPath.() -> Boolean)?
    ): Flow<MyPath> {
        TODO("Not yet implemented")
    }

    override fun listRecursively(
        contains: String?,
        filter: (MyPath.() -> Boolean)?
    ): Flow<MyPath> {
        TODO("Not yet implemented")
    }

    override fun fileWith(name: String): UriFile {
        TODO("Not yet implemented")
    }

    override fun dirWith(name: String): UriDirectory {
        TODO("Not yet implemented")
    }

    override suspend fun mkDir(name: String): UriDirectory? =
        documentFile()?.createDirectory(name)?.uri?.path?.let { UriDirectory(it) }

    override suspend fun mkFile(name: String): UriFile {
        val parentUri = getParentUri(path) // 補助関数で親のURIを取得
        val parentDoc = DocumentFile.fromTreeUri(context, parentUri)
        val mimeType =
            if (dir) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream"
        val newFile = parentDoc?.createFile(mimeType, name ?: "new_item")
            ?: throw Exception("Failed to create")
        return UriMyPath(context, newFile.path)
    }

    override suspend infix fun moveFrom(destination: MyDirectory) =
        if (destination is UriDirectory) moveFrom(destination)
        else super moveFrom destination

    suspend infix fun moveFrom(destination: UriDirectory) {
        val destUri = destination.rawPath.toUri()
        val parentUri = getParentUri(path)
        val destParentUri = getParentUri(destUri)

        // API 24+ の移動処理
        val resultUri = DocumentsContract.moveDocument(
            context.contentResolver, path, parentUri, destParentUri
        ) ?: throw Exception("Move failed")

        return UriMyPath(context, resultUri)
    }

    override suspend infix fun copyFrom(destination: MyDirectory) =
        if (destination is UriDirectory) copyFrom(destination)
        else super copyFrom destination

    suspend fun copyFrom(destination: UriDirectory) {
        // API 24+ DocumentsContract.copyDocument
        val resultUri = DocumentsContract.copyDocument(
            context.contentResolver, path, destination.path
        ) ?: throw Exception("Copy failed")

        return UriMyPath(context, resultUri)
    }
}