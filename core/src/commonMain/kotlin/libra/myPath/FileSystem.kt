package libra.myPath

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.all
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.files.FileMetadata

interface FileSystem {
    suspend infix fun name(path: Path): String?
    suspend infix fun exists(path: Path): Boolean
    suspend infix fun metadata(path: Path): FileMetadata?
    suspend infix fun isTreePath(path: Path): Boolean? = metadata(path)?.isDirectory


    suspend infix fun delete(path: Path): Boolean
    suspend infix fun source(path: Path): RawSource
    suspend fun sink(path: Path, append: Boolean = false): RawSink

    suspend fun copyFrom(path: Path, from: FileEntry): Boolean = withContext(Dispatchers.IO) {
        check(isTreePath(path) != true) { " < $path, $from" }
        require(from.exists()) { " < $path, $from" }

        from.source().buffered().use { it.transferTo(sink(path)) }
        metadata(path)?.size == from.metadata()?.size
    }

    suspend fun moveFrom(path: Path, from: FileEntry): Boolean = withContext(Dispatchers.IO) {
        runCatching { copyFrom(path, from) }.onSuccess { if (it) from.delete() }.getOrThrow()
    }

    // directory
    suspend fun findFile(path: Path, name: String): FileEntry?
    suspend fun createFile(path: Path, name: String): FileEntry?

    suspend fun findDirectory(path: Path, name: String): DirectoryEntry?
    suspend fun createDirectory(path: Path, name: String): DirectoryEntry?


    infix fun list(path: Path): Flow<Entry>

    data class ListRecursivelyReturn(
        val cwd: DirectoryEntry,
        val dirs: List<DirectoryEntry>,
        val files: List<FileEntry>,
        val steps: List<String>
    )

    fun listRecursively(
        path: Path, maxDepth: Int = 100
    ): Flow<ListRecursivelyReturn> = flow {
        check(metadata(path)?.isDirectory == true) { " < $path, $maxDepth" }
        check(isTreePath(path) == true) { " < $path, $maxDepth" }

        val queue = ArrayDeque<Pair<DirectoryEntry, List<String>>>()
        queue.add(Pair(DirectoryEntry(path, this@FileSystem), emptyList()))

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()

            val (cwd, steps) = queue.removeFirst()
            val dirs = mutableListOf<DirectoryEntry>()
            val files = mutableListOf<FileEntry>()

            list(cwd.path).collect {
                when (it) {
                    is FileEntry -> files.add(it)
                    is DirectoryEntry -> dirs.add(it)
                }
            }

            emit(ListRecursivelyReturn(cwd, dirs, files, steps))

            if (steps.size < maxDepth) {
                dirs.forEach {
                    val nextSteps = steps + (name(it.path)
                        ?: throw IllegalStateException(" < $path, $maxDepth, $it"))
                    queue.add(Pair(it, nextSteps))
                }
            }
        }
    }.flowOn(Dispatchers.IO)


    suspend infix fun deleteRecursively(path: Path): Boolean = withContext(Dispatchers.IO) {
        check(metadata(path)?.isDirectory == true) { " < $path" }
        check(isTreePath(path) == true) { " < $path" }

        val dirDepths: MutableList<Pair<Path, Int>> = mutableListOf()
        val mutex = Mutex()

        val result = coroutineScope {
            listRecursively(path).map { (cwd, _, files, steps) ->
                mutex.withLock {
                    dirDepths.add(Pair(cwd.path, steps.size))
                }

                files.map {
                    async { delete(it.path) }
                }.awaitAll().all { it }
            }.all { it }
        }

        if (!result) return@withContext false

        dirDepths.sortedByDescending { it.second }.forEach { (dir, _) -> delete(dir) }

        !exists(path)
    }


    suspend fun copyFrom(path: Path, from: DirectoryEntry): Boolean = withContext(Dispatchers.IO) {
        check(metadata(path)?.isDirectory == true) { " < $path, $from" }
        check(isTreePath(path) == true) { " < $path, $from" }

        val dirCache = mutableMapOf<List<String>, Path>()
        dirCache[emptyList()] = path

        coroutineScope {
            from.listRecursively().map { (_, _, files, steps) ->
                val wd = dirCache.getOrPut(steps) {
                    val parentSteps = steps.dropLast(1)
                    val parentPath = dirCache[parentSteps]
                        ?: throw IllegalStateException(" < $path, $from, $steps")
                    val currentDirName = steps.last()

                    findOrCreateDirectory(parentPath, currentDirName)?.path
                        ?: throw IllegalStateException(" < $path, $from, $parentPath, $currentDirName, $steps")
                }

                files.map {
                    async {
                        val file = findOrCreateFile(
                            wd, it.name() ?: throw IllegalStateException(" < $path, $from, $it")
                        ) ?: return@async false

                        copyFrom(file.path, it)
                    }
                }.awaitAll().all { it }
            }.all { it }
        }
    }

    suspend fun moveFrom(path: Path, from: DirectoryEntry): Boolean = withContext(Dispatchers.IO) {
        runCatching { copyFrom(path, from) }.onSuccess { if (it) from.deleteRecursively() }
            .getOrThrow()
    }
}

suspend fun FileSystem.findOrCreateFile(path: Path, name: String): FileEntry? =
    findFile(path, name) ?: createFile(path, name)

suspend fun FileSystem.findOrCreateDirectory(path: Path, name: String): DirectoryEntry? =
    findDirectory(path, name) ?: createDirectory(path, name)
