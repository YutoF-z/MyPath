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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.files.FileMetadata
import libra.myPath.DirectoryEntry
import libra.myPath.Entry
import libra.myPath.FileEntry
import libra.myPath.FileSystem
import libra.myPath.Path
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object UriFileSystem : FileSystem, KoinComponent {
    @JvmStatic
    val context: Context by inject()

    fun toUri(path: Path): Uri = path.path.toUri()
    fun Uri.toPath(): Path = Path(toString())

    suspend fun documentFile(path: Path): DocumentFile? = when (isTreePath(path)) {
        true -> DocumentFile.fromTreeUri(context, toUri(path))
        false -> DocumentFile.fromSingleUri(context, toUri(path))
    }

    override suspend fun name(path: Path): String? =
        withContext(Dispatchers.IO) { documentFile(path)?.name }

    override suspend fun exists(path: Path): Boolean = withContext(Dispatchers.IO) {
        documentFile(path)?.exists() ?: false
    }

    override suspend fun metadata(path: Path): FileMetadata? = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            toUri(path), arrayOf(
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE
            ), null, null, null
        )?.use {
            if (!it.moveToFirst()) return@use null

            val isDirectory = it.isDirectory() ?: return@use null
            FileMetadata(
                isRegularFile = !isDirectory, isDirectory = isDirectory, size = it.size() ?: 0L
            )
        }
    }

    override suspend fun resolveParent(path: Path): Path? = withContext(Dispatchers.IO) {
        val uri = toUri(path)

        if (!DocumentsContract.isTreeUri(uri)) return@withContext null

        runCatching {
            val docId = DocumentsContract.getDocumentId(uri)
            val parentDocId = when {
                docId.contains('/') -> docId.substringBeforeLast('/')
                docId.contains(':') && !docId.endsWith(':') -> docId.substringBeforeLast(':') + ":"
                else -> return@runCatching null
            }

            if (parentDocId.isEmpty() || parentDocId == docId) return@runCatching null

            DocumentsContract.buildDocumentUriUsingTree(uri, parentDocId).toPath()
        }.getOrNull()
    }

    override suspend fun delete(path: Path): Boolean = withContext(Dispatchers.IO) {
        documentFile(path)?.delete()
        !exists(path)
    }

    override suspend fun isTreePath(path: Path): Boolean = withContext(Dispatchers.IO) {
        DocumentsContract.isTreeUri(toUri(path))
    }

    override suspend fun source(path: Path): RawSource = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(toUri(path))?.asSource()
            ?: throw IllegalStateException(" < $path, ${exists(path)}")
    }

    override suspend fun sink(path: Path, append: Boolean): RawSink = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(toUri(path), if (append) "wa" else "w")?.asSink()
            ?: throw IllegalStateException(" < $path, $append, ${exists(path)}")
    }

    override suspend fun moveFrom(
        path: Path, from: FileEntry
    ): Boolean = withContext(Dispatchers.IO) {
        if (from.fileSystem === this@UriFileSystem) {
            val sourceParentUri = (from.parent ?: resolveParent(from.path))?.let { toUri(it) }

            if (sourceParentUri != null) {
                val rtn = runCatching {
                    DocumentsContract.moveDocument(
                        context.contentResolver, toUri(from.path), sourceParentUri, toUri(path)
                    ) != null
                }.onFailure { println(it) }.getOrNull() == true

                if (rtn) return@withContext true
            }
        }

        super.moveFrom(path, from)
    }

    override suspend fun findFile(
        path: Path, name: String
    ): FileEntry? = withContext(Dispatchers.IO) {
        check(isTreePath(path)) { " < $path, $name" }
        documentFile(path)?.findFile(name)?.takeIf { !it.isDirectory }?.uri?.toPath()
            ?.let { FileEntry(it, this@UriFileSystem, path) }
    }

    override suspend fun createFile(
        path: Path, name: String
    ): FileEntry? = withContext(Dispatchers.IO) {
        check(isTreePath(path)) { " < $path, $name" }

        val existing = findFile(path, name)
        if (existing != null) return@withContext existing

        val extension = name.substringAfterLast('.', "")
        val mimeType = if (extension.isNotEmpty()) {
            android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(extension.lowercase()) ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }

        documentFile(path)?.createFile(mimeType, name)?.uri?.toPath()
            ?.let { FileEntry(it, this@UriFileSystem, path) }
    }

    override suspend fun findDirectory(
        path: Path, name: String
    ): DirectoryEntry? = withContext(Dispatchers.IO) {
        check(isTreePath(path)) { " < $path, $name" }
        documentFile(path)?.findFile(name)?.takeIf { it.isDirectory }?.uri?.toPath()
            ?.let { DirectoryEntry(it, this@UriFileSystem, path) }
    }

    override suspend fun createDirectory(
        path: Path, name: String
    ): DirectoryEntry? = withContext(Dispatchers.IO) {
        check(isTreePath(path)) { " < $path, $name" }

        val existing = findDirectory(path, name)
        if (existing != null) return@withContext existing

        documentFile(path)?.createDirectory(name)?.uri?.toPath()
            ?.let { DirectoryEntry(it, this@UriFileSystem, path) }
    }

    override fun list(path: Path): Flow<Entry> = flow {
        val uri = toUri(path)
        val docId = DocumentsContract.getDocumentId(uri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId)

        context.contentResolver.query(
            childrenUri, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ), null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val childDocId = cursor.getString(
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                )

                val childPath =
                    DocumentsContract.buildDocumentUriUsingTree(uri, childDocId).toPath()

                when (cursor.isDirectory()) {
                    true -> DirectoryEntry(childPath, this@UriFileSystem, path)
                    false -> FileEntry(childPath, this@UriFileSystem, path)
                    null -> throw IllegalStateException(" < $path, $childDocId, ${exists(path)}")
                }.let { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun moveFrom(
        path: Path, from: DirectoryEntry
    ): Boolean = withContext(Dispatchers.IO) {
        if (from.fileSystem === this@UriFileSystem) {
            val sourceParentUri = (from.parent ?: resolveParent(from.path))?.let { toUri(it) }

            if (sourceParentUri != null) {
                val rtn = runCatching {
                    DocumentsContract.moveDocument(
                        context.contentResolver, toUri(from.path), sourceParentUri, toUri(path)
                    ) != null
                }.onFailure { println(it) }.getOrNull() == true

                if (rtn) return@withContext true
            }
        }

        super.moveFrom(path, from)
    }
}


/**
 * projection need a [DocumentsContract.Document.COLUMN_FLAGS]
 */
fun Cursor.flags(): UriFlags? = getIntOrNull(
    getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS)
)?.let { UriFlags(it) }

/**
 * projection need a [DocumentsContract.Document.COLUMN_DISPLAY_NAME]
 */
fun Cursor.name(): String? =
    getStringOrNull(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))

/**
 * projection need a [DocumentsContract.Document.COLUMN_MIME_TYPE]
 */
fun Cursor.isDirectory(): Boolean? = getStringOrNull(
    getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
)?.let { it == DocumentsContract.Document.MIME_TYPE_DIR }

/**
 * projection need a [DocumentsContract.Document.COLUMN_SIZE]
 */
fun Cursor.size(): Long? =
    getLongOrNull(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))
        ?: getLongOrNull(getColumnIndexOrThrow(OpenableColumns.SIZE))