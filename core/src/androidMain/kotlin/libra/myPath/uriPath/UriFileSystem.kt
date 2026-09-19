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
            arrayOf(
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            ),
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
        documentFile(path)?.delete()
        !exists(path)
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

    override suspend fun moveFrom(
        path: Path, from: FileEntry
    ): Boolean = withContext(Dispatchers.IO) {
        if (from.fileSystem === this@UriFileSystem) from.resolveParent()?.path?.toUri()
            ?.let { sourceParentUri ->
                val rtn = runCatching {
                    DocumentsContract.moveDocument(
                        context.contentResolver,
                        from.path.path.toUri(),
                        sourceParentUri,
                        path.path.toUri()
                    ) != null
                }.onFailure { println(it) }.getOrNull() == true

                if (rtn) return@withContext true
            }


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
        val existing = file(path, name)
        if (existing != null) return@withContext existing

        val extension = name.substringAfterLast('.', "")
        val mimeType = if (extension.isNotEmpty()) {
            android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(extension.lowercase()) ?: "application/octet-stream"
        } else {
            "application/octet-stream"
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
        val existing = directory(path, name)
        if (existing != null) return@withContext existing

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
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val childDocId = cursor.getString(
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                )

                val childPath =
                    DocumentsContract.buildDocumentUriUsingTree(uri, childDocId)

                when (cursor.isDirectory()) {
                    true -> DirectoryEntry(Path(childPath.toString()), this@UriFileSystem, path)
                    false -> FileEntry(Path(childPath.toString()), this@UriFileSystem, path)
                    null -> error(" exists: ${exists(path)}. < $path, $childDocId")
                }.let { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun moveFrom(
        path: Path, from: DirectoryEntry
    ): Boolean = withContext(Dispatchers.IO) {
        if (from.fileSystem === this@UriFileSystem) from.resolveParent()?.path?.toUri()
            ?.let { sourceParentUri ->
                val rtn = runCatching {
                    DocumentsContract.moveDocument(
                        context.contentResolver,
                        from.path.path.toUri(),
                        sourceParentUri,
                        path.path.toUri()
                    ) != null
                }.onFailure { println(it) }.getOrNull() == true

                if (rtn) return@withContext true
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