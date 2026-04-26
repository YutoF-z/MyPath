package libra.myPath.uriPath

import android.provider.DocumentsContract.Document

@JvmInline
value class UriMimeType(val mimeType: String) {
    val isDirectory: Boolean get() = mimeType == Document.MIME_TYPE_DIR
    val extension: String get() = mimeType.substringAfterLast('/')
}
