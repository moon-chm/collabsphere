package plugins

import com.collabsphere.util.CloudinaryService
import io.ktor.util.AttributeKey
import java.io.File

/** Provider boundary for avatar writes; application tests can inject an isolated fake. */
internal interface AvatarStorage {
    suspend fun upload(file: File, publicId: String): String
    suspend fun delete(publicId: String)
}

internal object CloudinaryAvatarStorage : AvatarStorage {
    override suspend fun upload(file: File, publicId: String) = CloudinaryService.uploadAvatar(file, publicId)
    override suspend fun delete(publicId: String) = CloudinaryService.deleteAvatar(publicId)
}

internal val AvatarStorageAttribute = AttributeKey<AvatarStorage>("CollabSphereAvatarStorage")
