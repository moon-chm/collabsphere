package com.collabsphere.app.model

import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Tracks repository poll loops so logout can stop them before account data is cleared. */
object BackgroundSyncRegistry {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val retiredJobs = ConcurrentHashMap.newKeySet<Job>()

    fun register(key: String, job: Job): Boolean {
        val accepted = AtomicBoolean(false)
        jobs.compute(key) { _, current ->
            if (current == null) {
                accepted.set(true)
                job
            } else current
        }
        return accepted.get()
    }

    fun replace(key: String, job: Job) {
        val previous = jobs.put(key, job)
        if (previous != null && previous !== job) {
            retiredJobs += previous
            previous.invokeOnCompletion { retiredJobs.remove(previous) }
            previous.cancel()
        }
    }

    fun unregister(key: String, job: Job) {
        jobs.remove(key, job)
    }

    suspend fun cancelAllAndJoin() {
        val active = (jobs.values + retiredJobs).distinct()
        active.forEach { it.cancel() }
        active.joinAll()
        active.forEach { job ->
            jobs.entries.forEach { entry -> jobs.remove(entry.key, job) }
            retiredJobs.remove(job)
        }
    }
}
