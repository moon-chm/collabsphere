package com.collabsphere.app.model

import kotlin.random.Random

/**
 * A single, shared way to mint a placeholder id for a row created offline, before the server has
 * assigned a real one. Server ids are always positive autoincrement values, so drawing from the
 * negative range guarantees a locally-created row can never collide with one synced down later —
 * unlike several ad-hoc schemes this replaced (plain positive Room autoincrement, or a timestamp
 * truncated into the positive Int range), which could and did collide with real server ids.
 */
object TempId {
    fun next(): Int = -(1 + Random.nextInt(0, Int.MAX_VALUE - 1))

    fun nextLong(): Long = -(1L + Random.nextLong(0, Long.MAX_VALUE - 1))
}
