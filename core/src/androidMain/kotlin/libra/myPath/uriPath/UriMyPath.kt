package libra.myPath.uriPath

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import libra.myPath.MyPath
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
@SerialName("UriMyPath")
sealed class UriMyPath : MyPath {
    @Transient
    val uri: Uri = rawPath.toUri()
    abstract val documentFile: DocumentFile?

    companion object : KoinComponent {
        @JvmStatic
        protected val context: Context by inject()
    }

    @Transient
    override var name: String? = null
        protected set

    @Transient
    override var metadata: FileMetadata? = null
        protected set


    override suspend fun statOrNull(): MyPath? {
//    Document.COLUMN_DISPLAY_NAME
//    Document.COLUMN_FLAGS
//    Document.COLUMN_LAST_MODIFIED
//    Document.COLUMN_MIME_TYPE
//    Document.COLUMN_SIZE

        metadata = withContext(Dispatchers.IO) {
            context.contentResolver.query(
                uri, null, null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name =
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))

                    val isDirectory = cursor.getString(
                        cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    ) == DocumentsContract.Document.MIME_TYPE_DIR

                    FileMetadata(
                        isDirectory = isDirectory,
                        isRegularFile = !isDirectory &&,
                        size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                    )
                } else null
            }
        }

        metadata ?: return null
        return asMyFile() ?: asMyDirectory()
    }


    final override suspend fun rm() {
        DocumentsContract.deleteDocument(context.contentResolver, uri)
    }
}