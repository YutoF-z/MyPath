package libra.myPath

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class Path(val path: String) {
    override fun toString() = path

    val prefix: String?
        get() = path.substringBefore("://", "").let {
            if (1 < it.length) it else null
        }
}