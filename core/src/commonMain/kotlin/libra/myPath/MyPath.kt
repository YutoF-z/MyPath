package libra.myPath

import kotlinx.serialization.Polymorphic
import okio.FileMetadata


@Polymorphic
interface MyPath {
    val rawPath: String

    suspend fun name(): String?

    suspend fun exists(): Boolean

    suspend fun metadata(): FileMetadata?

    suspend fun rm()

    companion object {
        private val SCHEME_REGEX by lazy { Regex("""^\w{2,}://""") }
        fun String.stripPrefix(): String = replace(SCHEME_REGEX, "")
    }
}


suspend inline fun <R> MyPath.onEach(
    crossinline onFile: suspend MyFile.() -> R,
    crossinline onDirectory: suspend MyDirectory.() -> R
): R = when (this) {
    is MyFile -> onFile()
    is MyDirectory -> onDirectory()
    else -> throw IllegalStateException("$this is UnClassified")
}

suspend infix fun MyPath.moveFrom(destination: MyPath) = onEach(
    { if (destination is MyFile) moveFrom(destination) else null },
    { if (destination is MyDirectory) moveFrom(destination) else null }
)

suspend infix fun MyPath.copyFrom(destination: MyPath) = onEach(
    { if (destination is MyFile) copyFrom(destination) else null },
    { if (destination is MyDirectory) copyFrom(destination) else null }
)