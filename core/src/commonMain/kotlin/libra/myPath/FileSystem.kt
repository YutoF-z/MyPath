package libra.myPath

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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
import kotlinx.serialization.Polymorphic

@Polymorphic
interface FileSystem {
    suspend infix fun name(path: Path): String?
    suspend infix fun exists(path: Path): Boolean
    suspend infix fun metadata(path: Path): FileMetadata?
    suspend infix fun isTreePath(path: Path): Boolean? = metadata(path)?.isDirectory
    suspend infix fun resolveParent(path: Path): Path?


    suspend infix fun delete(path: Path): Boolean
    suspend infix fun source(path: Path): RawSource
    suspend fun sink(path: Path, append: Boolean = false): RawSink

    suspend fun copyFrom(path: Path, from: FileEntry): Boolean = withContext(Dispatchers.IO) {
        check(isTreePath(path) != true) { "$path is a tree path. < ${from.path}" }
        require(from.exists()) { "$from does not exist. < ${from.path}" }

        sink(path).buffered().use { it.transferFrom(from.source()) }
        metadata(path)?.size == from.metadata()?.size
    }

    suspend fun moveFrom(path: Path, from: FileEntry): Boolean = withContext(Dispatchers.IO) {
        runCatching { copyFrom(path, from) }.onSuccess { if (it) from.delete() }.getOrThrow()
    }

    // directory
    suspend fun file(path: Path, name: String): FileEntry?
    suspend fun createFile(path: Path, name: String): FileEntry

    suspend fun directory(path: Path, name: String): DirectoryEntry?
    suspend fun createDirectory(path: Path, name: String): DirectoryEntry


    infix fun list(path: Path): Flow<Entry>

    fun listRecursively(
        path: Path,
        maxDepth: Int = 100
    ): Flow<Pair<Entry, List<String>>> = flow {
        check(isTreePath(path) == true) { "$path is not a tree path. < $maxDepth" }

        val visited = mutableSetOf<Path>()
        val queue = ArrayDeque<Pair<DirectoryEntry, List<String>>>()

        queue.add(DirectoryEntry(path, this@FileSystem) to emptyList())
        visited.add(path)

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()

            val (cwd, steps) = queue.removeFirst()

            list(cwd.path).collect { entry ->
                emit(entry to steps)

                if (entry is DirectoryEntry && steps.size < maxDepth) {
                    if (visited.add(entry.path)) {
                        val dirName = name(entry.path) ?: entry.path.path.substringAfterLast('/')
                        queue.add(entry to (steps + dirName))
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)


    suspend infix fun deleteRecursively(path: Path): Boolean = withContext(Dispatchers.IO) {
        check(isTreePath(path) == true) { "$path is not a tree path." }

        val dirDepths: MutableList<Pair<Path, Int>> = mutableListOf()
        val mutex = Mutex()

        dirDepths.add(path to 0)

        val result = listRecursively(path).map { (entry, steps) ->
            when (entry) {
                is FileEntry -> entry.delete()
                is DirectoryEntry -> {
                    entry.resolveParent()
                        ?.also { mutex.withLock { dirDepths.add(it to steps.size) } }
                        .let { it != null }
                }
            }
        }.all { it }

        if (!result) return@withContext false

        dirDepths.sortedByDescending { it.second }.forEach { (dir, _) -> delete(dir) }

        !exists(path)
    }


    suspend fun copyFrom(path: Path, from: DirectoryEntry): Boolean =
        withContext(Dispatchers.IO) {
            check(isTreePath(path) == true) { "$path is not a tree path. < ${from.path}" }

            val dirCache = mutableMapOf<List<String>, Path>()
            dirCache[emptyList()] = path

            coroutineScope {
                from.listRecursively().map { (entry, steps) ->
                    val cwd = dirCache.getOrPut(steps) {
                        val parentSteps = steps.dropLast(1)
                        val parentPath = dirCache[parentSteps]
                            ?: error("$parentSteps not in dirCache. < $path, $from, $steps")
                        val currentDirName = steps.last()

                        findOrCreateDirectory(parentPath, currentDirName).path
                    }

                    if (entry is FileEntry) {
                        val file = findOrCreateFile(
                            cwd, entry.name() ?: error("$entry.name is null. < $path, $from")
                        )
                        copyFrom(file.path, entry)
                    } else true
                }.all { it }
            }
        }

    suspend fun moveFrom(path: Path, from: DirectoryEntry): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { copyFrom(path, from) }.onSuccess { if (it) from.deleteRecursively() }
                .getOrThrow()
        }
}

suspend fun FileSystem.findOrCreateFile(path: Path, name: String): FileEntry =
    file(path, name).let {
        if (it == null || !it.exists()) createFile(path, name)
        else it
    }

suspend fun FileSystem.findOrCreateDirectory(path: Path, name: String): DirectoryEntry =
    directory(path, name).let {
        if (it == null || !it.exists()) createDirectory(path, name)
        else it
    }
