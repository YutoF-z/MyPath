package libra.myPath

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.files.FileMetadata

interface FileSystem {
    suspend fun name(path: Path): String?

    suspend fun exists(path: Path): Boolean

    suspend fun metadata(path: Path): FileMetadata?

    suspend fun isTreePath(path: Path): Boolean = metadata(path)?.isDirectory ?: false

    // file
    suspend fun delete(path: Path)
    suspend fun source(path: Path): Source
    suspend fun sink(path: Path, append: Boolean = false): Sink

    suspend fun copyFrom(path: Path, from: FileEntry) = withContext(Dispatchers.IO) {
        sink(path).use { from.source().transferTo(it) > 0 }
    }

    suspend fun moveFrom(path: Path, from: FileEntry) = withContext(Dispatchers.IO) {
        runCatching {
            copyFrom(path, from)
        }.getOrNull()?.also {
            if (it) from.delete()
        }
    }


    // directory
    fun file(path: Path, name: String): Path?
    suspend fun mkFile(path: Path, name: String): Path?
    fun dir(path: Path, name: String): Path?
    suspend fun mkDir(path: Path, name: String): Path?


    fun list(path: Path): Flow<Path>

    data class ListRecursivelyReturn(
        val cwd: Path,
        val dirs: List<Path>,
        val files: List<Path>,
        val steps: List<String>
    )

    fun listRecursively(
        path: Path,
        maxDepth: Int = 100
    ): Flow<ListRecursivelyReturn> = flow {
        val queue = ArrayDeque<Pair<Path, List<String>>>()
        queue.add(Pair(path, emptyList()))

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()

            val (currentPath, currentSteps) = queue.removeFirst()
            val dirs = mutableListOf<Path>()
            val files = mutableListOf<Path>()

            list(currentPath).collect { item ->
                if (isTreePath(item)) {
                    dirs.add(item)
                } else {
                    files.add(item)
                }
            }

            emit(
                ListRecursivelyReturn(
                    cwd = currentPath,
                    dirs = dirs,
                    files = files,
                    steps = currentSteps
                )
            )

            if (currentSteps.size < maxDepth) {
                dirs.forEach { dir ->
                    val nextSteps = currentSteps + name(dir).toString()
                    queue.add(Pair(dir, nextSteps))
                }
            }
        }
    }.flowOn(Dispatchers.IO)


    @OptIn(ExperimentalCoroutinesApi::class)
suspend fun deleteRecursively(path: Path) = withContext(Dispatchers.IO) {
    val dirDepths = mutableSetOf<Pair<Path, Int>>()

    listRecursively(path).collect { (cwd, _, files, steps) ->
        // 1. ファイルはその場で即座に削除（安全）
        for (file in files) {
            delete(file)
        }

        // 2. 訪れたディレクトリ（cwd）とその深さを記憶するだけにする
        // （dirs をここに追加する必要はありません。dirs は後で必ず cwd として流れてくるため）
        dirDepths.add(Pair(cwd, steps.size))
    }

    // 3. 収集したディレクトリを「最深部（steps.size が大きい順）」から順番に削除
    dirDepths
        .sortedByDescending { it.second }
        .forEach { (dir, _) ->
            delete(dir)
        }
}


    suspend fun copyFrom(path: Path, from: DirectoryEntry) {
        withContext(Dispatchers.IO) {
            copy(destination, this@DirectoryEntry)
        }
    }

    suspend fun moveFrom(path: Path, from: DirectoryEntry) {
        withContext(Dispatchers.IO) {
            runCatching {
                copyFrom(path, from)
                exists(path)
            }.getOrNull()?.also {
                if (it) from.deleteRecursively()
            }
        }
    }

    private suspend fun copy(start: Path, base: Path) {
        list(start)
            .buffer(capacity = 64)
            .collect {
                if (isTreePath(it)) {
                    base.file(name(it).toString())?.copyFrom(this)
                } else {
                    copy(this, base.mkDir(name(it).toString())!!)
                }
            }
    }
}
