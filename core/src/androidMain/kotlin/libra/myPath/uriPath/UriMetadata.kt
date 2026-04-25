package libra.myPath.uriPath

import android.os.Build
import android.provider.DocumentsContract.Document
import androidx.annotation.RequiresApi
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

data class UriMetadata(
    val flags: Int,
    val lastModified: Long,
    val mimeType: String
) {
    val strTime: LocalDateTime
        get() = Instant.fromEpochMilliseconds(lastModified).toLocalDateTime(TimeZone.currentSystemDefault())

    val isDirectory: Boolean get() = mimeType == Document.MIME_TYPE_DIR
    val extension: String get() = mimeType.substringAfterLast('/')

    val flagDirBlocksOpenDocumentTree: Boolean
        @RequiresApi(Build.VERSION_CODES.R)
        get() = flags and Document.FLAG_DIR_BLOCKS_OPEN_DOCUMENT_TREE != 0
    val flagDirPrefersGrid: Boolean get() = flags and Document.FLAG_DIR_PREFERS_GRID != 0
    val flagDirPrefersLastModified: Boolean get() = flags and Document.FLAG_DIR_PREFERS_LAST_MODIFIED != 0
    val flagDirSupportsCreate: Boolean get() = flags and Document.FLAG_DIR_SUPPORTS_CREATE != 0
    val flagPartial: Boolean
        @RequiresApi(Build.VERSION_CODES.Q)
        get() = flags and Document.FLAG_PARTIAL != 0
    val flagSupportsCopy: Boolean get() = flags and Document.FLAG_SUPPORTS_COPY != 0
    val flagSupportsDelete: Boolean get() = flags and Document.FLAG_SUPPORTS_DELETE != 0
    val flagSupportsMetadata: Boolean
        @RequiresApi(Build.VERSION_CODES.Q)
        get() = flags and Document.FLAG_SUPPORTS_METADATA != 0
    val flagSupportsMove: Boolean get() = flags and Document.FLAG_SUPPORTS_MOVE != 0
    val flagSupportsRemove: Boolean get() = flags and Document.FLAG_SUPPORTS_REMOVE != 0
    val flagSupportsRename: Boolean get() = flags and Document.FLAG_SUPPORTS_RENAME != 0
    val flagSupportsSettings: Boolean
        @RequiresApi(Build.VERSION_CODES.O)
        get() = flags and Document.FLAG_SUPPORTS_SETTINGS != 0
    val flagSupportsThumbnail: Boolean get() = flags and Document.FLAG_SUPPORTS_THUMBNAIL != 0
    val flagSupportsWrite: Boolean get() = flags and Document.FLAG_SUPPORTS_WRITE != 0
    val flagVirtualDocument: Boolean get() = flags and Document.FLAG_VIRTUAL_DOCUMENT != 0
    val flagWebLinkable: Boolean
        @RequiresApi(Build.VERSION_CODES.O)
        get() = flags and Document.FLAG_WEB_LINKABLE != 0
}
