package libra.myPath.samba

import kotlinx.serialization.Serializable

@Serializable
data class SambaServer(
    val host: List<String>,
    val port: Int?,
    val shareName: String,
    val username: String,
    val password: String,
    val domain: String?,
)
