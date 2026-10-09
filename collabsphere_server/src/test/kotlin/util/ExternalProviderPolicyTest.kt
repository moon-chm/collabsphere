package util

import com.collabsphere.util.ExternalProviderPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalProviderPolicyTest {
    @Test
    fun `providers stay enabled by default`() {
        assertFalse(ExternalProviderPolicy.areDisabled(emptyMap()))
    }

    @Test
    fun `test profile disables external providers`() {
        assertTrue(ExternalProviderPolicy.areDisabled(mapOf("COLLABSPHERE_DISABLE_EXTERNAL_PROVIDERS" to "YES")))
        assertFalse(ExternalProviderPolicy.areDisabled(mapOf("COLLABSPHERE_DISABLE_EXTERNAL_PROVIDERS" to "yes")))
    }
}
