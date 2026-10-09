package com.collabsphere.util

/** Production file uploads must use durable storage; local disk remains available for development. */
internal object StoragePolicy {
    fun requiresDurableStorage(environment: Map<String, String> = System.getenv()): Boolean =
        environment["APP_ENV"].equals("production", ignoreCase = true)
}
