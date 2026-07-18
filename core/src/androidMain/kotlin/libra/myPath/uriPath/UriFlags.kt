package libra.myPath.uriPath

import android.os.Build
import android.provider.DocumentsContract.Document
import androidx.annotation.RequiresApi
import libra.myPath.uriPath.UriPath.Companion.flags

@JvmInline
value class UriFlags(val flags: Int) {
    val isDirBlocksOpenDocumentTree: Boolean
        @RequiresApi(Build.VERSION_CODES.R)
        get() = flags and Document.FLAG_DIR_BLOCKS_OPEN_DOCUMENT_TREE != 0
    val isDirPrefersGrid: Boolean get() = flags and Document.FLAG_DIR_PREFERS_GRID != 0
    val isDirPrefersLastModified: Boolean get() = flags and Document.FLAG_DIR_PREFERS_LAST_MODIFIED != 0
    val isDirSupportsCreate: Boolean get() = flags and Document.FLAG_DIR_SUPPORTS_CREATE != 0
    val isPartial: Boolean
        @RequiresApi(Build.VERSION_CODES.Q)
        get() = flags and Document.FLAG_PARTIAL != 0
    val isSupportsCopy: Boolean get() = flags and Document.FLAG_SUPPORTS_COPY != 0
    val isSupportsDelete: Boolean get() = flags and Document.FLAG_SUPPORTS_DELETE != 0
    val isSupportsMetadata: Boolean
        @RequiresApi(Build.VERSION_CODES.Q)
        get() = flags and Document.FLAG_SUPPORTS_METADATA != 0
    val isSupportsMove: Boolean get() = flags and Document.FLAG_SUPPORTS_MOVE != 0
    val isSupportsRemove: Boolean get() = flags and Document.FLAG_SUPPORTS_REMOVE != 0
    val isSupportsRename: Boolean get() = flags and Document.FLAG_SUPPORTS_RENAME != 0
    val isSupportsSettings: Boolean
        @RequiresApi(Build.VERSION_CODES.O)
        get() = flags and Document.FLAG_SUPPORTS_SETTINGS != 0
    val isSupportsThumbnail: Boolean get() = flags and Document.FLAG_SUPPORTS_THUMBNAIL != 0
    val isSupportsWrite: Boolean get() = flags and Document.FLAG_SUPPORTS_WRITE != 0
    val isVirtualDocument: Boolean get() = flags and Document.FLAG_VIRTUAL_DOCUMENT != 0
    val isWebLinkable: Boolean
        @RequiresApi(Build.VERSION_CODES.O)
        get() = flags and Document.FLAG_WEB_LINKABLE != 0
}


suspend fun UriPath.flags(): UriFlags? = useCursor { flags() }