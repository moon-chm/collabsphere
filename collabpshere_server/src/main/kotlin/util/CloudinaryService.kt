package com.collabsphere.util

import com.cloudinary.Cloudinary
import com.cloudinary.util.ObjectUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin wrapper around the Cloudinary Java SDK.
 *
 * Credentials are read from environment variables so they are never committed to source control:
 *   CLOUDINARY_CLOUD_NAME  (e.g. iw9s4kjr)
 *   CLOUDINARY_API_KEY
 *   CLOUDINARY_API_SECRET
 *
 * On Render, add these three vars in Dashboard → Environment before deploying.
 */
object CloudinaryService {

    private val cloudinary: Cloudinary by lazy {
        val cloudName = System.getenv("CLOUDINARY_CLOUD_NAME")
            ?: error("CLOUDINARY_CLOUD_NAME env var not set")
        val apiKey = System.getenv("CLOUDINARY_API_KEY")
            ?: error("CLOUDINARY_API_KEY env var not set")
        val apiSecret = System.getenv("CLOUDINARY_API_SECRET")
            ?: error("CLOUDINARY_API_SECRET env var not set")

        Cloudinary(
            ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key",    apiKey,
                "api_secret", apiSecret,
                "secure",     true        // always use https://
            )
        )
    }

    /**
     * Uploads raw image bytes to the `avatars` folder in Cloudinary.
     *
     * @param bytes      Raw image bytes from the multipart upload.
     * @param publicId   Desired public_id, e.g. "avatar_42". Using a stable ID means
     *                   re-uploading overwrites the old file automatically — no orphans.
     * @return The secure HTTPS URL of the uploaded image.
     */
    suspend fun uploadAvatar(bytes: ByteArray, publicId: String): String =
        withContext(Dispatchers.IO) {
            @Suppress("UNCHECKED_CAST")
            val result = cloudinary.uploader().upload(
                bytes,
                ObjectUtils.asMap(
                    "public_id",    publicId,
                    "folder",       "avatars",
                    "overwrite",    true,
                    "resource_type","image",
                    "format",       "jpg"   // normalise to jpg so URLs are predictable
                )
            ) as Map<String, Any>

            result["secure_url"] as? String
                ?: error("Cloudinary upload returned no secure_url")
        }

    /**
     * Deletes an avatar from Cloudinary by its public_id.
     * The full Cloudinary public_id for an avatar is "avatars/{publicId}".
     * Safe to call even if the resource no longer exists (Cloudinary returns "not found" — not an error).
     */
    suspend fun deleteAvatar(publicId: String): Unit =
        withContext(Dispatchers.IO) {
            runCatching {
                cloudinary.uploader().destroy(
                    "avatars/$publicId",
                    ObjectUtils.asMap("resource_type", "image")
                )
            }
            // Ignore errors — if deletion fails the avatar is just orphaned on Cloudinary,
            // which is not a blocking issue for the user.
        }
}
