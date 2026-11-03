package libra.myPath.googleDrive

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.files.FileMetadata
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import libra.myPath.DirectoryEntry
import libra.myPath.Entry
import libra.myPath.FileEntry
import libra.myPath.FileSystem
import libra.myPath.Path

@Serializable
class GoogleDriveFileSystem(val config: GoogleDriveConfig) : FileSystem {
    constructor(accessToken: String) : this(GoogleDriveConfig(accessToken = accessToken))

    @Transient
    private val tokenMutex = Mutex()

    @Transient
    private var currentAccessToken: String = config.accessToken

    @Transient
    private val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(DriveJson)
        }
    }

    private suspend inline fun authorizedRequest(
        crossinline block: suspend (token: String) -> HttpResponse
    ): HttpResponse {
        val token = tokenMutex.withLock { currentAccessToken }
        val response = block(token)
        if (response.status == HttpStatusCode.Unauthorized && refreshAccessToken()) {
            val refreshed = tokenMutex.withLock { currentAccessToken }
            return block(refreshed)
        }
        return response
    }

    private fun HttpRequestBuilder.bearerAuth(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private suspend fun refreshAccessToken(): Boolean = tokenMutex.withLock {
        val refreshToken = config.refreshToken ?: return@withLock false
        val clientId = config.clientId ?: return@withLock false

        val response = runCatching {
            client.submitForm(
                url = OAUTH_TOKEN_URL,
                formParameters = Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                    append("client_id", clientId)
                    config.clientSecret?.let { append("client_secret", it) }
                }
            )
        }.getOrNull() ?: return@withLock false

        if (!response.status.isSuccess()) return@withLock false

        val tokenResp = runCatching { response.body<TokenRefreshResponse>() }.getOrNull()
            ?: return@withLock false

        currentAccessToken = tokenResp.accessToken
        true
    }

    private fun escapeQueryValue(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")

    private suspend fun resolveFileId(path: Path): String? {
        val raw = path.path
            .removePrefix("gdrive://")
            .removePrefix("googledrive://")
            .trim()

        if (raw.isEmpty() || raw == "/" || raw == ROOT_ID) return ROOT_ID
        if ('/' !in raw) return raw

        val segments = raw.trim('/').split('/')
        var currentId = ROOT_ID
        for (i in segments.indices) {
            val segment = segments[i]
            if (segment.isEmpty() || (i == 0 && segment == ROOT_ID)) continue
            val child = findChildByName(currentId, segment, directoryOnly = null) ?: return null
            currentId = child.id
        }
        return currentId
    }

    private suspend fun fetchDriveFile(fileId: String): DriveFile? = withContext(Dispatchers.IO) {
        val response = authorizedRequest { token ->
            client.get("$FILES_API_URL/$fileId") {
                bearerAuth(token)
                parameter("fields", FILE_FIELDS)
                parameter("supportsAllDrives", true)
            }
        }
        if (!response.status.isSuccess()) return@withContext null
        runCatching { response.body<DriveFile>() }
            .getOrNull()
            ?.takeIf { it.trashed != true }
    }

    private suspend fun findChildByName(
        parentId: String,
        name: String,
        directoryOnly: Boolean?
    ): DriveFile? = withContext(Dispatchers.IO) {
        val escapedParent = escapeQueryValue(parentId)
        val escapedName = escapeQueryValue(name)
        val mimeCondition = when (directoryOnly) {
            true -> " and mimeType = '${DriveFile.FOLDER_MIME_TYPE}'"
            false -> " and mimeType != '${DriveFile.FOLDER_MIME_TYPE}'"
            null -> ""
        }
        val query = "'$escapedParent' in parents and name = '$escapedName' and trashed = false$mimeCondition"

        val response = authorizedRequest { token ->
            client.get(FILES_API_URL) {
                bearerAuth(token)
                parameter("q", query)
                parameter("fields", "files($FILE_FIELDS)")
                parameter("pageSize", 1)
                parameter("supportsAllDrives", true)
                parameter("includeItemsFromAllDrives", true)
            }
        }
        if (!response.status.isSuccess()) return@withContext null
        runCatching { response.body<DriveFileList>() }
            .getOrNull()
            ?.files
            ?.firstOrNull()
    }

    override suspend fun name(path: Path): String? = withContext(Dispatchers.IO) {
        val fileId = resolveFileId(path) ?: return@withContext null
        fetchDriveFile(fileId)?.name
    }

    override suspend fun exists(path: Path): Boolean = withContext(Dispatchers.IO) {
        val fileId = resolveFileId(path) ?: return@withContext false
        if (fileId == ROOT_ID) return@withContext true
        fetchDriveFile(fileId) != null
    }

    override suspend fun metadata(path: Path): FileMetadata? = withContext(Dispatchers.IO) {
        val fileId = resolveFileId(path) ?: return@withContext null
        fetchDriveFile(fileId)?.let {
            FileMetadata(
                isRegularFile = !it.isDirectory,
                isDirectory = it.isDirectory,
                size = it.size ?: 0L
            )
        }
    }

    override suspend fun isTreePath(path: Path): Boolean? = withContext(Dispatchers.IO) {
        val fileId = resolveFileId(path) ?: return@withContext null
        if (fileId == ROOT_ID) return@withContext true
        fetchDriveFile(fileId)?.isDirectory
    }

    override suspend fun resolveParent(path: Path): Path? = withContext(Dispatchers.IO) {
        val fileId = resolveFileId(path) ?: return@withContext null
        if (fileId == ROOT_ID) return@withContext null
        fetchDriveFile(fileId)?.parents?.firstOrNull()?.let { Path(it) }
    }

    override suspend fun delete(path: Path): Boolean = withContext(Dispatchers.IO) {
        val fileId = resolveFileId(path) ?: return@withContext false
        val response = authorizedRequest { token ->
            client.delete("$FILES_API_URL/$fileId") {
                bearerAuth(token)
                parameter("supportsAllDrives", true)
            }
        }
        response.status.isSuccess()
    }

    private suspend fun resolveDirectoryId(path: Path, childName: String): String? {
        val fileId = resolveFileId(path) ?: return null
        if (fileId == ROOT_ID) return ROOT_ID
        val dir = fetchDriveFile(fileId)
        check(dir?.isDirectory == true) { "$path is not a tree path. < $childName" }
        return fileId
    }

    override suspend fun file(path: Path, name: String): FileEntry? = withContext(Dispatchers.IO) {
        val parentId = resolveDirectoryId(path, name) ?: return@withContext null
        findChildByName(parentId, name, directoryOnly = false)?.let {
            FileEntry(Path(it.id), this@GoogleDriveFileSystem, Path(parentId))
        }
    }

    override suspend fun createFile(path: Path, name: String): FileEntry =
        withContext(Dispatchers.IO) {
            val parentId = checkNotNull(resolveDirectoryId(path, name)) {
                "Parent does not exist: $path"
            }
            findChildByName(parentId, name, directoryOnly = false)?.let {
                return@withContext FileEntry(Path(it.id), this@GoogleDriveFileSystem, Path(parentId))
            }

            val response = authorizedRequest { token ->
                client.post(FILES_API_URL) {
                    bearerAuth(token)
                    parameter("fields", FILE_FIELDS)
                    parameter("supportsAllDrives", true)
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateDriveFileRequest(
                            name = name,
                            parents = listOf(parentId)
                        )
                    )
                }
            }
            check(response.status.isSuccess()) {
                "Failed to create file '$name' in $path (status=${response.status})"
            }
            val created = response.body<DriveFile>()
            FileEntry(Path(created.id), this@GoogleDriveFileSystem, Path(parentId))
        }

    override suspend fun directory(path: Path, name: String): DirectoryEntry? =
        withContext(Dispatchers.IO) {
            val parentId = resolveDirectoryId(path, name) ?: return@withContext null
            findChildByName(parentId, name, directoryOnly = true)?.let {
                DirectoryEntry(Path(it.id), this@GoogleDriveFileSystem, Path(parentId))
            }
        }

    override suspend fun createDirectory(path: Path, name: String): DirectoryEntry =
        withContext(Dispatchers.IO) {
            val parentId = checkNotNull(resolveDirectoryId(path, name)) {
                "Parent does not exist: $path"
            }
            findChildByName(parentId, name, directoryOnly = true)?.let {
                return@withContext DirectoryEntry(Path(it.id), this@GoogleDriveFileSystem, Path(parentId))
            }

            val response = authorizedRequest { token ->
                client.post(FILES_API_URL) {
                    bearerAuth(token)
                    parameter("fields", FILE_FIELDS)
                    parameter("supportsAllDrives", true)
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateDriveFileRequest(
                            name = name,
                            mimeType = DriveFile.FOLDER_MIME_TYPE,
                            parents = listOf(parentId)
                        )
                    )
                }
            }
            check(response.status.isSuccess()) {
                "Failed to create directory '$name' in $path (status=${response.status})"
            }
            val created = response.body<DriveFile>()
            DirectoryEntry(Path(created.id), this@GoogleDriveFileSystem, Path(parentId))
        }

    override fun list(path: Path): Flow<Entry> = flow {
        val parentId = checkNotNull(resolveFileId(path)) { "Directory does not exist: $path" }
        val parentPath = Path(parentId)
        val escapedParent = escapeQueryValue(parentId)
        val query = "'$escapedParent' in parents and trashed = false"
        var pageToken: String? = null

        do {
            val response = authorizedRequest { token ->
                client.get(FILES_API_URL) {
                    bearerAuth(token)
                    parameter("q", query)
                    parameter("fields", "nextPageToken,files($FILE_FIELDS)")
                    parameter("pageSize", 1000)
                    parameter("supportsAllDrives", true)
                    parameter("includeItemsFromAllDrives", true)
                    pageToken?.let { parameter("pageToken", it) }
                }
            }
            check(response.status.isSuccess()) {
                "Failed to list directory $path (status=${response.status})"
            }
            val page = response.body<DriveFileList>()
            for (item in page.files) {
                val itemPath = Path(item.id)
                val entry = if (item.isDirectory) {
                    DirectoryEntry(itemPath, this@GoogleDriveFileSystem, parentPath)
                } else {
                    FileEntry(itemPath, this@GoogleDriveFileSystem, parentPath)
                }
                emit(entry)
            }
            pageToken = page.nextPageToken
        } while (!pageToken.isNullOrEmpty())
    }.flowOn(Dispatchers.IO)

    private suspend fun downloadBytes(fileId: String): ByteArray {
        val response = authorizedRequest { token ->
            client.get("$FILES_API_URL/$fileId") {
                bearerAuth(token)
                parameter("alt", "media")
                parameter("supportsAllDrives", true)
            }
        }
        check(response.status.isSuccess()) {
            "Failed to download file $fileId (status=${response.status})"
        }
        return response.readRawBytes()
    }

    override suspend fun source(path: Path): RawSource = withContext(Dispatchers.IO) {
        val fileId = checkNotNull(resolveFileId(path)) { "File does not exist: $path" }
        val meta = fetchDriveFile(fileId)
        check(meta != null && !meta.isDirectory) { "$path is not a file or does not exist." }
        Buffer().apply { write(downloadBytes(fileId)) }
    }

    override suspend fun sink(path: Path, append: Boolean): RawSink = withContext(Dispatchers.IO) {
        val fileId = checkNotNull(resolveFileId(path)) { "File does not exist: $path" }
        val meta = fetchDriveFile(fileId)
        check(meta?.isDirectory != true) { "$path is a directory." }

        val initialBuffer = Buffer()
        if (append && meta != null && (meta.size ?: 0L) > 0L) {
            initialBuffer.write(downloadBytes(fileId))
        }
        GoogleDriveSink(fileId, initialBuffer)
    }

    inner class GoogleDriveSink(
        private val fileId: String,
        private val buffer: Buffer
    ) : RawSink {
        private var closed = false

        override fun write(source: Buffer, byteCount: Long) {
            require(byteCount >= 0) { "byteCount < 0: $byteCount < $fileId" }
            check(!closed) { "Sink is already closed: $fileId" }
            source.readAtMostTo(buffer, byteCount)
        }

        override fun flush() = Unit

        override fun close() {
            if (closed) return
            closed = true
            val bytes = buffer.use { it.readByteArray() }
            runBlocking(Dispatchers.IO) {
                val response = authorizedRequest { token ->
                    client.patch("$UPLOAD_FILES_API_URL/$fileId") {
                        bearerAuth(token)
                        parameter("uploadType", "media")
                        parameter("supportsAllDrives", true)
                        contentType(ContentType.Application.OctetStream)
                        setBody(bytes)
                    }
                }
                check(response.status.isSuccess()) {
                    "Failed to upload file $fileId (status=${response.status})"
                }
            }
        }
    }

    companion object {
        const val ROOT_ID = "root"
        val ROOT = Path(ROOT_ID)

        private const val FILES_API_URL = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_FILES_API_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val FILE_FIELDS = "id,name,mimeType,size,parents,trashed"

        private val DriveJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    }
}
