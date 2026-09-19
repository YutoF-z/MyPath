package libra.myPath.uriPath

import android.provider.DocumentsContract.Document
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class UriFlags(val flags: Int) {
    val isDirBlocksOpenDocumentTree: Boolean get() = flags and Document.FLAG_DIR_BLOCKS_OPEN_DOCUMENT_TREE != 0
    val isDirPrefersGrid: Boolean get() = flags and Document.FLAG_DIR_PREFERS_GRID != 0
    val isDirPrefersLastModified: Boolean get() = flags and Document.FLAG_DIR_PREFERS_LAST_MODIFIED != 0
    val isDirSupportsCreate: Boolean get() = flags and Document.FLAG_DIR_SUPPORTS_CREATE != 0
    val isPartial: Boolean get() = flags and Document.FLAG_PARTIAL != 0
    val isSupportsCopy: Boolean get() = flags and Document.FLAG_SUPPORTS_COPY != 0
    val isSupportsDelete: Boolean get() = flags and Document.FLAG_SUPPORTS_DELETE != 0
    val isSupportsMetadata: Boolean get() = flags and Document.FLAG_SUPPORTS_METADATA != 0
    val isSupportsMove: Boolean get() = flags and Document.FLAG_SUPPORTS_MOVE != 0
    val isSupportsRemove: Boolean get() = flags and Document.FLAG_SUPPORTS_REMOVE != 0
    val isSupportsRename: Boolean get() = flags and Document.FLAG_SUPPORTS_RENAME != 0
    val isSupportsSettings: Boolean get() = flags and Document.FLAG_SUPPORTS_SETTINGS != 0
    val isSupportsThumbnail: Boolean get() = flags and Document.FLAG_SUPPORTS_THUMBNAIL != 0
    val isSupportsWrite: Boolean get() = flags and Document.FLAG_SUPPORTS_WRITE != 0
    val isVirtualDocument: Boolean get() = flags and Document.FLAG_VIRTUAL_DOCUMENT != 0
    val isWebLinkable: Boolean get() = flags and Document.FLAG_WEB_LINKABLE != 0
}