package libra.myPath

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Transient
import okio.FileMetadata


@Polymorphic
interface MyPath {
    val rawPath: String

    @Transient
    val name: String?

    suspend fun statOrNull(): MyPath?
    suspend fun metadataOrNull(): FileMetadata?
}

suspend fun MyPath.stat(): MyPath = statOrNull() ?: this


suspend inline fun <R> MyPath.onEach(
    crossinline onFile: suspend MyFile.() -> R,
    crossinline onDirectory: suspend MyDirectory.() -> R,
    crossinline onElse: suspend MyPath.() -> R,
): R = when (this) {
    is MyFile -> onFile()
    is MyDirectory -> onDirectory()
    else -> onElse()
}

suspend infix fun MyPath.moveFrom(destination: MyPath): MyPath? = onEach(
    { if (destination is MyFile) moveFrom(destination) else null },
    { if (destination is MyDirectory) moveFrom(destination) else null },
    { null }
)

suspend infix fun MyPath.copyFrom(destination: MyPath): MyPath? = onEach(
    { if (destination is MyFile) copyFrom(destination) else null },
    { if (destination is MyDirectory) copyFrom(destination) else null },
    { null }
)

suspend fun MyPath.rm() = onEach(
    { rm() },
    { rm() },
    { null }
)