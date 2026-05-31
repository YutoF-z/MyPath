package libra.myPath

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Transient
import okio.FileMetadata


@Polymorphic
interface MyPathInterface {
    val rawPath: String

    @Transient
    val name: String?

    suspend fun statOrNull(): MyPathInterface?
    suspend fun metadataOrNull(): FileMetadata?

    suspend fun rm()
}

suspend fun MyPathInterface.stat(): MyPathInterface = statOrNull() ?: this