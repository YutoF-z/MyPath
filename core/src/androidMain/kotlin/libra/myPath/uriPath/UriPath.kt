package libra.myPath.uriPath

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import libra.myPath.MyPath
import okio.FileMetadata
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


@Serializable
@SerialName("UriPath")
sealed class UriPath : MyPath {
    val path: Uri get() = rawPath.toUri()
    abstract fun documentFile(): DocumentFile?

    final override suspend fun exists(): Boolean = withContext(Dispatchers.IO) {
        documentFile()?.exists() ?: false
    }

    final override suspend fun name(): String? =
        withContext(Dispatchers.IO) { documentFile()?.name }

    final override suspend fun metadata(): FileMetadata? = useCursor { metadataOrNull() }

    final override suspend fun rm() {
        withContext(Dispatchers.IO) {
            documentFile()?.delete()
        }
    }

    companion object : KoinComponent {
        @JvmStatic
        val context: Context by inject()

        fun Cursor.name(): String? =
            getStringOrNull(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))

        fun Cursor.flags(): UriFlags? = getIntOrNull(
            getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS)
        )?.let { UriFlags(it) }

        fun Cursor.metadataOrNull(): FileMetadata? {
            val isDirectory = getStringOrNull(
                getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            )
                ?.let { it == DocumentsContract.Document.MIME_TYPE_DIR }
                ?: return null

            return FileMetadata(
                isRegularFile = !isDirectory,
                isDirectory = isDirectory,
                size = getLongOrNull(getColumnIndexOrThrow(OpenableColumns.SIZE)),
                lastModifiedAtMillis = getLongOrNull(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
            )
        }
    }
}

suspend inline fun <R> UriPath.useCursor(crossinline block: Cursor.() -> R): R? =
    withContext(Dispatchers.IO) {
        UriPath.context.contentResolver.query(
            path, null, null, null, null
        )?.use {
            if (it.moveToFirst()) it.block()
            else null
        }
    }
