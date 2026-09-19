package libra.myPath.smb

import kotlinx.serialization.Serializable
import uniffi.samba_cargo.SmbServerConfig

@Serializable
data class SambaServerConfig(
    val host: List<String>,
    val port: UShort?,
    val shareName: String,
    val username: String,
    val password: String,
    val domain: String?
) {
    fun toSmbServerConfig(): SmbServerConfig = SmbServerConfig(
        host = host,
        port = port,
        shareName = shareName,
        username = username,
        password = password,
        domain = domain
    )

    companion object {
        operator fun invoke(config: SmbServerConfig): SambaServerConfig = SambaServerConfig(
            host = config.host,
            port = config.port,
            shareName = config.shareName,
            username = config.username,
            password = config.password,
            domain = config.domain
        )
    }
}
