package libra.myPath


interface MyPath : MyPathInterface {
    suspend fun asMyDirectory(mustExist: Boolean = true): MyDirectory?
    suspend fun asMyFile(mustExist: Boolean = true): MyFile?

    override suspend fun rm() {
        asMyDirectory(false)?.rm() ?: asMyFile(false)?.rm()
    }
}

suspend inline fun <R> MyPath.onEach(
    crossinline onFile: suspend MyFile.() -> R,
    crossinline onDirectory: suspend MyDirectory.() -> R,
    crossinline onDefault: suspend MyPath.() -> R
): R = asMyFile()?.run { onFile() }
    ?: asMyDirectory()?.run { onDirectory() }
    ?: onDefault()


suspend fun MyPath.moveFrom(destination: MyPathInterface): MyPathInterface? = onEach(
    {
        when (destination) {
            is MyFile -> moveFrom(destination)
            is MyPath if destination.metadataOrNull()?.isDirectory == false ->
                destination.asMyFile(false)?.let { moveFrom(it) }
            else -> null
        }
    },
    {
        when (destination) {
            is MyDirectory -> moveFrom(destination)
            is MyPath if destination.metadataOrNull()?.isRegularFile == false ->
                destination.asMyDirectory(false)?.let { moveFrom(it) }
            else -> null
        }
    },
    { null }
)

suspend fun MyPath.copyFrom(destination: MyPathInterface): MyPathInterface? = onEach(
    {
        when (destination) {
            is MyFile -> copyFrom(destination)
            is MyPath if destination.metadataOrNull()?.isDirectory == false ->
                destination.asMyFile(false)?.let { copyFrom(it) }

            else -> null
        }
    },
    {
        when (destination) {
            is MyDirectory -> copyFrom(destination)
            is MyPath if destination.metadataOrNull()?.isRegularFile == false ->
                destination.asMyDirectory(false)?.let { copyFrom(it) }
            else -> null
        }
    },
    { null }
)

suspend fun MyPath.mk(dir: Boolean = true): MyPathInterface? = when (dir) {
    true -> asMyDirectory(false)?.mk()
    false -> asMyFile(false)?.mk()
}