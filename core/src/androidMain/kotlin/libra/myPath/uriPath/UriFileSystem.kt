package libra.myPath.uriPath

import android.content.Context
import android.database.Cursor
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
import kotlinx.serialization.Serializable
import libra.myPath.DirectoryEntry
import libra.myPath.Entry
import libra.myPath.FileEntry
import libra.myPath.FileSystem
import libra.myPath.Path
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
object UriFileSystem : FileSystem, KoinComponent {
    @JvmStatic
    val context: Context by inject()

    suspend fun documentFile(path: Path): DocumentFile? = when (isTreePath(path)) {
        true -> DocumentFile.fromTreeUri(context, path.path.toUri())
        false -> DocumentFile.fromSingleUri(context, path.path.toUri())
    }

    override suspend fun name(path: Path): String? =
        withContext(Dispatchers.IO) { documentFile(path)?.name }

    override suspend fun exists(path: Path): Boolean = withContext(Dispatchers.IO) {
        documentFile(path)?.exists() ?: false
    }

    override suspend fun metadata(path: Path): FileMetadata? = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            path.path.toUri(),
            METADATA_PROJECTION,
            null,
            null,
            null
        )?.use {
            if (!it.moveToFirst()) return@use null

            val isDirectory = it.isDirectory() ?: return@use null
            FileMetadata(
                isRegularFile = !isDirectory,
                isDirectory = isDirectory,
                size = it.size() ?: 0L
            )
        }
    }

    override suspend fun resolveParent(path: Path): Path? = withContext(Dispatchers.IO) {
        val uri = path.path.toUri()

        if (!DocumentsContract.isTreeUri(uri)) return@withContext null

        runCatching {
            val docId = DocumentsContract.getDocumentId(uri)
            val parentDocId = when {
                docId.contains('/') -> docId.substringBeforeLast('/')
                docId.contains(':') && !docId.endsWith(':') -> docId.substringBeforeLast(':') + ":"
                else -> return@runCatching null
            }

            if (parentDocId.isEmpty() || parentDocId == docId) return@runCatching null

            Path(DocumentsContract.buildDocumentUriUsingTree(uri, parentDocId).toString())
        }.getOrNull()
    }

    override suspend fun delete(path: Path): Boolean = withContext(Dispatchers.IO) {
        documentFile(path)?.delete() == true || !exists(path)
    }

    override suspend fun isTreePath(path: Path): Boolean = withContext(Dispatchers.IO) {
        DocumentsContract.isTreeUri(path.path.toUri())
    }

    override suspend fun source(path: Path): RawSource = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(path.path.toUri())?.asSource()
            ?: error("exists: ${exists(path)}. < $path")
    }

    override suspend fun sink(path: Path, append: Boolean): RawSink = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(path.path.toUri(), if (append) "wa" else "w")
            ?.asSink()
            ?: error("exists: ${exists(path)}. < $path, $append")
    }

    private suspend fun tryMoveDocument(from: Entry, targetParentPath: Path): Boolean {
        if (from.fileSystem !== this@UriFileSystem) return false
        val sourceParentUri = from.resolveParent()?.path?.toUri() ?: return false
        return runCatching {
            DocumentsContract.moveDocument(
                context.contentResolver,
                from.path.path.toUri(),
                sourceParentUri,
                targetParentPath.path.toUri()
            ) != null
        }.getOrDefault(false)
    }

    override suspend fun moveFrom(
        path: Path, from: FileEntry
    ): Boolean = withContext(Dispatchers.IO) {
        if (tryMoveDocument(from, path)) return@withContext true
        super.moveFrom(path, from)
    }

    override suspend fun file(
        path: Path, name: String
    ): FileEntry? = withContext(Dispatchers.IO) {
        check(isTreePath(path)) { "$path is not a tree path. < $name" }
        documentFile(path)?.findFile(name)
            ?.takeIf { !it.isDirectory }?.uri
            ?.let { FileEntry(Path(it.toString()), this@UriFileSystem, path) }
    }

    override suspend fun createFile(
        path: Path, name: String
    ): FileEntry = withContext(Dispatchers.IO) {
        file(path, name)?.let { return@withContext it }

        val extension = name.substringAfterLast('.', "")
        val mimeType = if (extension.isNotEmpty()) {
            android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(extension.lowercase()) ?: DEFAULT_MIME_TYPE
        } else {
            DEFAULT_MIME_TYPE
        }

        documentFile(path)?.createFile(mimeType, name)?.uri
            ?.let { FileEntry(Path(it.toString()), this@UriFileSystem, path) }
            ?: error("Failed to create file. < $path, $name")
    }

    override suspend fun directory(
        path: Path, name: String
    ): DirectoryEntry? = withContext(Dispatchers.IO) {
        check(isTreePath(path)) { "$path is not a tree path. < $name" }
        documentFile(path)?.findFile(name)
            ?.takeIf { it.isDirectory }?.uri
            ?.let { DirectoryEntry(Path(it.toString()), this@UriFileSystem, path) }
    }

    override suspend fun createDirectory(
        path: Path, name: String
    ): DirectoryEntry = withContext(Dispatchers.IO) {
        directory(path, name)?.let { return@withContext it }

        documentFile(path)?.createDirectory(name)?.uri
            ?.let { DirectoryEntry(Path(it.toString()), this@UriFileSystem, path) }
            ?: error("Failed to create directory. < $path, $name")
    }

    override fun list(path: Path): Flow<Entry> = flow {
        val uri = path.path.toUri()

        context.contentResolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(
                uri, DocumentsContract.getDocumentId(uri)
            ),
            LIST_PROJECTION,
            null,
            null,
            null
        )?.use { cursor ->
            val docIdCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val childDocId = cursor.getString(docIdCol)
                val mimeType = cursor.getStringOrNull(mimeCol)
                val childPath = Path(DocumentsContract.buildDocumentUriUsingTree(uri, childDocId).toString())

                val entry = when (mimeType) {
                    DocumentsContract.Document.MIME_TYPE_DIR ->
                        DirectoryEntry(childPath, this@UriFileSystem, path)
                    null -> error("exists: ${exists(path)}. < $path, $childDocId")
                    else ->
                        FileEntry(childPath, this@UriFileSystem, path)
                }
                emit(entry)
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun moveFrom(
        path: Path, from: DirectoryEntry
    ): Boolean = withContext(Dispatchers.IO) {
        if (tryMoveDocument(from, path)) return@withContext true
        super.moveFrom(path, from)
    }

    private const val DEFAULT_MIME_TYPE = "application/octet-stream"

    private val METADATA_PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE
    )

    private val LIST_PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_MIME_TYPE
    )
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
