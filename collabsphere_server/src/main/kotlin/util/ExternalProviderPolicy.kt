package com.collabsphere.util

internal object ExternalProviderPolicy {
    fun areDisabled(environment: Map<String, String> = System.getenv()): Boolean =
        environment["COLLABSPHERE_DISABLE_EXTERNAL_PROVIDERS"] == "YES"
}
