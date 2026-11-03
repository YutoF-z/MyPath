package libra.myPath.googleDrive

import kotlinx.serialization.Serializable

@Serializable
data class GoogleDriveConfig(
    val accessToken: String,
    val refreshToken: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null
)

