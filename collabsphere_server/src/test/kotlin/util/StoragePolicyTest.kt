package util

import com.collabsphere.util.StoragePolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoragePolicyTest {
    @Test
    fun `local development permits local storage fallback`() {
        assertFalse(StoragePolicy.requiresDurableStorage(mapOf("APP_ENV" to "development")))
        assertFalse(StoragePolicy.requiresDurableStorage(emptyMap()))
    }

    @Test
    fun `production requires durable storage`() {
        assertTrue(StoragePolicy.requiresDurableStorage(mapOf("APP_ENV" to "production")))
        assertTrue(StoragePolicy.requiresDurableStorage(mapOf("APP_ENV" to "PRODUCTION")))
    }
}
