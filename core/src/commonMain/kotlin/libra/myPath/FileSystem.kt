package libra.myPath

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.all
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
        val fromSize = requireNotNull(from.metadata()?.size) { "$from does not exist. < ${from.path}" }

        from.source().use { src ->
            sink(path).buffered().use { dst ->
                dst.transferFrom(src)
            }
        }
        metadata(path)?.size == fromSize
    }

    suspend fun moveFrom(path: Path, from: FileEntry): Boolean = withContext(Dispatchers.IO) {
        if (copyFrom(path, from)) {
            from.delete()
        } else {
            false
        }
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

        val visited = HashSet<Path>()
        val queue = ArrayDeque<Pair<DirectoryEntry, List<String>>>()

        queue.add(DirectoryEntry(path, this@FileSystem) to emptyList())
        visited.add(path)

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()

            val (cwd, steps) = queue.removeFirst()

            list(cwd.path).collect { entry ->
                emit(entry to steps)

                if (entry is DirectoryEntry && steps.size < maxDepth && visited.add(entry.path)) {
                    val dirName = name(entry.path) ?: entry.path.path.substringAfterLast('/')
                    queue.add(entry to (steps + dirName))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend infix fun deleteRecursively(path: Path): Boolean = withContext(Dispatchers.IO) {
        check(isTreePath(path) == true) { "$path is not a tree path." }

        val dirs = ArrayList<Path>()
        dirs.add(path)

        val filesDeleted = listRecursively(path).all { (entry, _) ->
            when (entry) {
                is FileEntry -> entry.delete()
                is DirectoryEntry -> {
                    dirs.add(entry.path)
                    true
                }
            }
        }

        if (!filesDeleted) return@withContext false

        for (i in dirs.lastIndex downTo 0) {
            delete(dirs[i])
        }

        !exists(path)
    }

    suspend fun copyFrom(path: Path, from: DirectoryEntry): Boolean =
        withContext(Dispatchers.IO) {
            check(isTreePath(path) == true) { "$path is not a tree path. < ${from.path}" }

            val dirCache = HashMap<List<String>, Path>()
            dirCache[emptyList()] = path

            suspend fun resolveTargetDir(steps: List<String>): Path =
                dirCache.getOrPut(steps) {
                    val parentPath = resolveTargetDir(steps.dropLast(1))
                    findOrCreateDirectory(parentPath, steps.last()).path
                }

            from.listRecursively().all { (entry, steps) ->
                val cwd = resolveTargetDir(steps)
                val entryName = entry.name() ?: error("$entry.name is null. < $path, $from")

                when (entry) {
                    is DirectoryEntry -> {
                        dirCache[steps + entryName] = findOrCreateDirectory(cwd, entryName).path
                        true
                    }
                    is FileEntry -> {
                        val targetFile = findOrCreateFile(cwd, entryName)
                        copyFrom(targetFile.path, entry)
                    }
                }
            }
        }

    suspend fun moveFrom(path: Path, from: DirectoryEntry): Boolean =
        withContext(Dispatchers.IO) {
            if (copyFrom(path, from)) {
                from.deleteRecursively()
            } else {
                false
            }
        }
}

suspend fun FileSystem.findOrCreateFile(path: Path, name: String): FileEntry =
    createFile(path, name)

suspend fun FileSystem.findOrCreateDirectory(path: Path, name: String): DirectoryEntry =
    createDirectory(path, name)
