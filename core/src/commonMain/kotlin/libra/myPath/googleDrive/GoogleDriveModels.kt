package libra.myPath.googleDrive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long? = null,
    val parents: List<String>? = null,
    val trashed: Boolean? = null
) {
    val isDirectory: Boolean
        get() = mimeType == FOLDER_MIME_TYPE

    companion object {
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    }
}

@Serializable
internal data class DriveFileList(
    val nextPageToken: String? = null,
    val files: List<DriveFile> = emptyList()
)

@Serializable
internal data class CreateDriveFileRequest(
    val name: String,
    val mimeType: String? = null,
    val parents: List<String>? = null
)

@Serializable
internal data class TokenRefreshResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Long? = null,
    @SerialName("token_type")
    val tokenType: String? = null
)

